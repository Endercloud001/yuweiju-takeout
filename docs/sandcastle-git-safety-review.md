# Sandcastle：危险 Git 命令文章的本地适用性

日期：2026-10-09（Asia/Shanghai）。范围：阅读与改进建议；未修改运行时、安装 hook、启动 AFK 或执行危险 Git 命令。

## 结论

值得采用的是“在操作执行前落实少量安全边界”的做法。当前本地 Sandcastle 使用 Codex，文章的 Claude Code `PreToolUse` 配置不能直接复制；文章给出的字符串正则只能减少常见误操作，不能证明所有危险命令都无法绕过。

原文：[Matt Pocock — This Hook Stops Claude Code Running Dangerous Git Commands](https://www.aihero.dev/this-hook-stops-claude-code-running-dangerous-git-commands)，页面标注更新于 2026-02-10。文章指出沙箱隔离与项目内 Git 安全是不同问题，演示执行前拦截、危险操作默认拒绝、可定制规则及安装后的实际验证。其默认列表包含 push、hard reset、强制 clean、强制删除分支及批量 checkout/restore。

一手来源核对：[Claude Code hooks 官方文档](https://code.claude.com/docs/en/hooks#exit-code-2)说明 `PreToolUse` 的退出码 2 会阻止工具调用，并向模型反馈 stderr；普通退出码 0 仍进入通常权限流程。其他错误退出码及 command hook 超时通常不会阻止执行，所以“脚本存在”不等于防护可靠。[作者 skill 源码](https://github.com/mattpocock/skills/blob/main/skills/misc/git-guardrails-claude-code/SKILL.md)只匹配 `Bash`；[过滤脚本](https://github.com/mattpocock/skills/blob/main/skills/misc/git-guardrails-claude-code/scripts/block-dangerous-git.sh)以 `jq` 提取命令，再用 `grep -qE` 匹配字符串。命令间额外参数、间接调用会造成漏报，文本中提到危险命令也可能误报；没有解析命令语义，也没有保证解析或依赖失败时拒绝执行。应采用其执行前约束思路，而非原样继承这些缺陷。

## 本地代码事实

本次只读检查的通用运行时为 `/home/endercloud/projects/yuweiju-sandcastle-env`，不是旧 #4 试点。发现线索来自 [AFK runtime 参考](../.agents/skills/yuweiju-afk/references/runtime.md)，实际行为以以下文件为准：

| 证据位置（相对于该运行时） | 观察与意义 |
| --- | --- |
| `.sandcastle/business-worker.mts:9` | 使用 `codex('gpt-6.1-sol', { effort: 'medium', captureSessions: false })`，未设置 `approvalsReviewer` |
| `node_modules/@ai-hero/sandcastle/dist/index.js:3226` | 安装包默认生成 `--dangerously-bypass-approvals-and-sandbox`；这解释了为什么不能依靠代理本身逐次审批 |
| `.sandcastle/common.mts:15` | 独立缓存、专用认证挂载，以及只读 skills/input；已有边界应保留 |
| `.sandcastle/common.mts:33` | 检查任务分支、启动提交，并保护已完成 #4 分支；这些是启动时检查 |
| `.sandcastle/common.mts:61` | hooks 为 `onSandboxReady` 资源记录、UID/登录检查及依赖安装，没有逐条命令拦截 |
| `node_modules/@ai-hero/sandcastle/dist/chunk-VOG34SRF.js:26454` | `resolveGitMounts` 将 linked worktree 对应的父 Git 目录纳入挂载，没有标记 readonly |
| `node_modules/@ai-hero/sandcastle/dist/chunk-CP3TYXZA.js:138` | Docker provider 将框架挂载与用户挂载一起传入容器，保留 readonly 字段 |
| `.sandcastle/common.mts:68`、`.sandcastle/prompt.md:4` | 已有取消、资源停止观察、进度与未提交成果保留；应继续使用 |

安装版本为 Sandcastle 0.12.0；镜像 Dockerfile 声明 Codex CLI 0.160.1。此处核对的是源码与构建声明，未重新启动容器确认当前镜像内版本。`prompt.md` 是历史 #4 指令，通用任务使用配置指定的 prompt，不能把历史提示词当成每轮都生效的规则。

由挂载实现推断：代理具有改写该独立 runtime 仓库共享 Git 元数据的能力；任务分支和 worktree 不等于所有分支引用隔离。这不是声称已经发生破坏，也不是声称 Windows 主仓库 `.git` 被直接挂载。

## 可采用的改进

### 1. 在执行前保护成果，补足事后复核

具体失败场景：AFK 代理为“清理失败状态”执行强制 clean，删除尚未提交的进度或新文件。随后取消保留机制只能保留剩余内容，独立检查也无法取回被删除的未跟踪文件。

另一个场景：代理从任务 worktree 强制删除其他分支，影响该 runtime 仓库已有任务成果的引用。启动时确认新分支不存在，不会约束运行中的引用变更。

Git 与版本号只能定位仍存在的版本，Git 不自动保存未跟踪文件；reflog 也不是未提交内容备份。数据库主键、事务、唯一约束对文件和 Git 引用没有作用；类型与普通测试能检查实现，却不能约束绕过审批的代理每次实际操作。因此可考虑少量针对破坏性操作的执行限制。无需新增 hash、冻结 contract、baseline 或通用 gate；已有安全措施继续保留。

### 2. 规则围绕 AFK 权限，保留正常本地提交

建议默认限制代理的全部 push、hard reset、强制 clean、强制删除分支，以及会丢弃现有修改的 restore/checkout。允许 status、diff、show、log，允许显式 stage 授权文件及普通任务提交。不要禁止整个 `checkout` 子命令，它也承担合法切换操作；恢复文件的范围和是否已有修改要具体判断。

当前 AFK 职责是交付本地任务提交、独立复核并等待接受。push 可交由获得本轮授权的宿主操作执行，不必让无人值守代理持有该能力。普通 push 是外部写入，和 hard reset 的风险原因不同，应分别解释。

规则仅适用于编码代理。Sandcastle 宿主编排仍需创建/清理本轮 worktree，依赖安装可能使用 Git；不能把代理限制粗暴套到所有宿主与准备命令上。需要例外时说明具体操作与授权，不允许代理自行修改限制重试。

### 3. 使用本地执行器可落实的入口，不直接搬 Claude 配置

`onSandboxReady` 可用于安装或检查防护，但它本身不会收到之后每条 Codex shell 命令。仅增加此生命周期 hook，不能宣称实现了文章的 `PreToolUse`。

若采用 Git wrapper，它应位于代理不可写的位置，并明确描述为防误操作措施。PATH wrapper 仍可能被 `/usr/bin/git`、其他程序或直接文件操作绕过；只读策略文件也不会自动约束绕过它的执行路径。若目标是防止任意绕过，需要执行器权限约束或缩小共享 Git 元数据的暴露范围，不能依靠正则宣称达成。

可进一步评估每任务独立 clone 的收益：其他任务分支不再共享同一 `.git`。但它不能保护本任务自己的未提交文件，也会改变成果回收路径，应在确认需要后评估；本轮不建议直接重构现有运行时。

### 4. 借鉴拒绝反馈，避免无人值守重复重试

拒绝信息应包含操作类别、拒绝原因和下一步，例如：“强制 clean 会删除未提交成果；保留当前 worktree，将阻碍写入进度，等待维护者处理。”

现有进度、日志和结果分类已经能承载反馈，无需另建审计系统。代理收到拒绝后不得换绝对路径、换 shell 或修改配置来重试。可继续已授权且无依赖的工作；如果被拒操作是完成任务的必要前提，则交付阻碍。输出应脱敏，避免把命令中的凭据 URL 写入报告。

### 5. 用实际入口测试保护，继续复用无模型验证

测试产物放项目 `.scratch/`，建立无远程、无认证的临时仓库，包含已提交文件、已修改文件、未跟踪文件及一个测试分支。通过拟采用的实际执行入口验证：

- 危险操作被拒后，文件内容、未跟踪成果和测试分支仍存在。
- status/diff、显式 stage、普通提交正常工作。
- 参数组合、`git -C`、绝对路径、alias、子脚本等是否覆盖；明确报告不能拦截的路径。
- 正常 Sandcastle 宿主清理未受误伤，取消后仍保留应保留的成果。
- 防护无法加载时报告准备失败，不默认为已经受保护。

文章建议清空上下文后试运行，以确认约束不依赖模型记忆。本地应把这点转化为实际入口测试；不要在真实项目或有有效远程/凭据的仓库测试 push 或破坏性命令。现有生命周期 smoke 是合适的集成位置，测试重点是拒绝后的实际状态，不能只断言错误文字。

## 优先顺序与范围

先明确代理操作权限与防护强度，再选择 Codex 可落实的入口；首批只覆盖已列明的破坏性操作、拒绝反馈和无模型正反例。评估共享 `.git` 隔离属于后续选择，不因这篇文章默认引入大规模防护框架。

本轮仅新增研究笔记。后端、管理端、小程序业务代码、API 和数据库无需修改。未验证新防护实现，也未声称现有运行时已能拦截危险命令。
