# Ponytail 接入 Sandcastle AFK Loop 的可行性调研

调研日期：2026-10-12（Asia/Shanghai）。范围：读取上游源码与本项目现有流程，提出接入建议；本轮没有安装插件、执行上游脚本、启动编码模型、修改运行时或技能、提交代码或写入 GitHub。

后续状态（2026-10-12）：维护者已确认方案 A、所有新 AFK 默认复查、预算不足保持未完成；项目技能与默认材料/提示已实装，见 [实施报告](ponytail-implementation-report.md)。下文保留初研事实与当时的未验证项；后续无模型验证不等于真实模型试点或收益验证。

## 结论

**可行，但不能把 `/ponytail` 当作一个已存在的“自动精简代码”流水线命令直接插入。** Ponytail 5.1.0 的 `/ponytail` 是编程模式；`/ponytail-review` 是只读审查报告；`/ponytail-audit` 是全仓审查报告。真正修改代码，需要任务明确要求模型依据 Ponytail 规则进行一次限定范围的精简，并在修改后重新检查。上游提供提示词、技能和适配器，没有提供本项目的阶段调度器。[U2][U3][U4][U5][L2]

推荐把这一轮精简安排在 **AFK 内部首次检查之后、最终检查和任务提交之前**，沿用同一个授权任务、分支、迭代和时间预算。这样仍处于“AFK 代码生成”内部收尾，逻辑顺序满足“生成→精简→`/retro`”，同时保留“最终任务提交→COMPLETE→资源停止检查→宿主独立验证→执行报告→`/retro`”的现有顺序。[L1][L2][L3]

如果需要一个模型上下文与 AFK 分开的精简步骤，可以在 AFK 已结束后，以它的任务提交为起点另做一次后处理，形成新的提交，然后独立验证**新提交**。这需要后续编码授权和预算；不能把已结束的运行自动重启或复用已有 AFK 分支。当前技能禁止未经授权重启失败、取消或不完整的运行，运行时还明确拒绝已存在的任务分支。[L1][L2][L3]

## 证据范围与版本

| 对象 | 本次核实的事实与限制 |
| --- | --- |
| Ponytail 上游 | GitHub 页面和 GitHub API/raw 源码均已读取；API 返回 `main` 提交 `9b58c1ffb790c075ca32e70a89cf1d80588f4abf`，提交时间 `2026-10-10T04:19:52Z`。`package.json` 与 `.codex-plugin/plugin.json` 均为 `5.1.0`。后文上游链接均指向该普通 Git 提交，便于回看本次证据。[U1][U6] |
| 项目协作规则 | 根 AGENTS 与 workflow 要求保留安全边界、兼容三端、记录真实验证结果。当前研究只增加文档，不改变三端业务。[L0] |
| 当前发现入口 | `.agents/skills/yuweiju-afk/references/runtime.md` 指向项目内 `.scratch/sandcastle-git-safety/runtime`；通过 WSL 只读 `git rev-parse HEAD` 核实该 clone 的 HEAD 为 `09c2962d30eef79a41f9f2baaefe8d2032c36bc3`。这不证明它已与远端最新 main 一致。[L2] |
| 后续改进来源 | `.sandcastle/verification.md` 指向 `.scratch/issue13-environment-improvements`。该目录源码可直接读取，但 Windows 和直接 WSL Git 均受其 `.git` 路径映射影响，未取得其 HEAD；本报告只引用可读源码与当前文档，不声称该目录版本等于最新 main。[L5][L6] |
| 证据保存 | 上游正文与 API 响应保存在项目内 `.scratch/ponytail-research/`，没有执行其中 JavaScript、安装或卸载命令。下载时猜测的 `commands/ponytail-review.md` 等路径返回 404，随后按真实 tree 读取 `.toml` 和对应技能；报告以成功读取的路径为据。 |

以上提交只是已有 Git 版本引用，没有增加 hash 校验机制、冻结 contract、baseline 或 gate。

## 上游实际做什么

### 1. `/ponytail` 是模式，不是一遍式代码变换

核心技能要求先读任务与连接代码，按“是否需要→复用项目已有实现→标准库/平台→已安装依赖→最小可读实现”的顺序选择方案。它会持续作用于会话，直到用户要求停止；支持 lite、full、ultra，默认 full。技能强调保留信任边界校验、防止数据丢失的错误处理、安全、可访问性和用户要求。[U2]

因此，只有开启模式不足以证明 AFK 结果已经过一次收尾精简。要实现用户的目标，任务提示必须明确：审查本次任务的完整差异、识别具体可删减项、应用符合验收要求的项，并重新检查。若在宿主会话开启模式，结束后应显式退出，避免继续影响 `/retro` 与交付讨论；纯文本模式退出与插件持久状态退出并非同一实现，完整插件需按它支持的模式切换处理。[U2][U5][U7]

### 2. `/ponytail-review` 不会替你改代码

当前 review 依次检查正确性、安全、真实负载、测试、性能和精简；读差异及连接的调用者、被调用函数、测试与 README。每项发现必须能举出具体输入或情境。它输出问题与最小修复建议，明确不修改代码。[U3]

没有指定范围时，它检查未提交修改；没有未提交修改时默认只检查最后一个提交。AFK 可能形成多个任务提交，且准备提交应与任务提交分开，所以必须明确传入“`startCommit` 到任务候选提交之间的任务差异与允许的文件”，不能只说“review last commit”，也不能把宿主其他未提交修改一起精简。[U3][L2]

audit 使用同类规则审查整个仓库，输出最多 20 项发现，也不修改代码。它适合单独的仓库诊断任务，不适合默认放进每轮 AFK，后者容易扩大范围和耗时。[U4]

### 3. 上游 hook 不是提交前强制拦截

Codex manifest 引用 `skills/` 和 `hooks/claude-codex-hooks.json`。这个配置注册 SessionStart、SubagentStart、UserPromptSubmit；职责是激活规则、注入上下文和跟踪模式，不注册 Git 的 pre-commit/pre-push，也没有针对 Bash 的提交拦截规则。因此“commit 或 push 前一定再审核并精简”不是当前上游自动保障的事实。[U6][U7]

激活脚本会尝试写模式状态，并可能生成代码地图、提示状态栏配置；相关操作采用 best-effort。完整插件带有技能文本以外的会话和用户配置副作用，不能当成纯只读 Markdown。默认安装插件还会从会话启动时激活规则，可能改变整轮生成行为，不再只是末尾的精简步骤。[U7][U8]

上游 README 的性能数字来自作者的 Claude Code 基准，不是本项目 Java/Vue/微信、Codex 模型与 Docker AFK 环境的测试结果。本报告不据此承诺减少多少代码、时间或费用。[U1]

## 与现有 AFK 流程的匹配

完整流程图见 [Sandcastle AFK Loop](../yuweiju-document/sandcastle-afk-loop.md)。这里按接入所需的边界展开。

| 现有事实 | 对接入的影响 |
| --- | --- |
| AFK 技能在启动前核实 issue、授权、三端影响、工作树与实际配置；必须指定任务范围、验收命令和停止条件。[L1][L2] | 精简也必须在同一范围内，不能据“更简洁”修改无关模块或删除验收行为。 |
| `main.ts` 只调用 `supervise('business-worker.mts')`；配置有 prompt、skillsDirectory、checkCommands、时间/迭代等字段，没有 ponytail/retro 阶段字段。[L3] | 无须为了试点新增阶段引擎；先在任务 prompt 收尾中安排一次精简即可。宿主独立验证与 retro 仍由外部流程执行。 |
| protected runtime worker 的可读源码使用 `gpt-6.1-sol`、medium、`captureSessions:false`；`common.mts` 默认一轮、30 分钟总时限、600 秒 idle、60 秒完成宽限。[L3] | 容器内精简耗时也消耗本轮预算。配置可能覆盖默认值，试点前应按真实配置核实，不能自动加时间或迭代。 |
| 可选 skillsDirectory 只读挂载到 `/home/agent/.agents/skills`；inputDirectory 也只读挂载；认证目录另挂到 `/home/agent/.codex`。[L3] | 技能正文适合以只读输入供模型阅读；只挂技能不能推定插件 hooks 已注册。不要为此改认证目录或安装完整插件。 |
| 技能要求显式 stage 任务文件、形成任务提交、检查范围，然后才发 COMPLETE；完成标记与任务提交必须分别判断。[L1][L2] | 同轮精简要放在最终提交之前；若在已提交候选后修改，旧 COMPLETE 与旧验证不能证明新候选完成。 |
| 总时限、取消、容器停止、日志和结果保留由 supervisor/runtime 管理，已有分支拒绝覆盖复用。[L2][L3] | 不能让 hook 暗中另起模型，也不能结束后偷偷重跑 supervisor。 |
| 独立 verifier 从指定提交建立快照，无模型、无认证挂载；快照不含未提交文件。[L5][L6] | 精简后必须先有新提交再验证；未提交的“看起来已改好”不会进入验证。 |
| Git guard 阻止容器 push、hard reset、实际 clean 和部分覆盖/强制改写；普通显式 add 与 commit 可用，存在已说明的绕过边界。[L4] | 保留该措施。Ponytail 提示词不是安全沙箱，也不能替代它；遇拒绝不得绕过。 |

**推论：**以明确 prompt 调用只读技能，技术结构与现有 Docker 挂载相容；是否能被当前容器 Codex 自动发现、自然语言调用是否足够可靠，以及精简质量是否提升，仍需一次获授权试点验证。只读文本可读取与“插件已正常运行”应分别记录。[U2][U6][L3]

## 两种接入方案

### 方案 A：同轮 AFK 收尾，优先建议

顺序：授权与准备 → AFK 实现 → 首次任务检查 → **一次 Ponytail 精简** → 最终任务检查 → 显式 stage、任务提交与范围确认 → COMPLETE → supervisor 退出并检查资源停止 → 宿主独立验证精确候选 → 执行报告 → `/retro`。[L1][L2][L3]

后续实施时，只需准备所需技能正文并在任务 prompt 加入收尾要求；使用现有 skillsDirectory 或 task-input 即可。两者任选一种，不必重复注入。若使用上游 review 技能，先让它输出发现，再在任务授权内应用已确认满足范围和验收的精简项；不能把 review 名称解释为它本身会改代码。[U3][L3]

建议精简要求使用以下含义的文字，具体验收命令沿用 issue：

> 完成实现并通过首次检查后，按指定 Ponytail 规则审查 startCommit 起本任务的差异及连接代码；仅应用能说明具体冗余或更简单等价实现的项。保留用户要求、项目层次、认证、归属检查、事务、错误处理及兼容接口。允许没有值得精简的项。随后重跑受影响检查和本任务全部必需验收，记录改动与未验证项，最后才形成任务提交并发送 COMPLETE。不得增加依赖、时间、迭代或任务范围；超时、持续失败或决策不明按现有停止规则处理。

这是建议的任务收尾约定，不是新增 gate。采用 full 即可；ultra 会进一步质疑需求，可能与已确认 issue 冲突，不建议作为首轮默认值。[U2][L1]

### 方案 B：已提交候选之后，独立后处理

顺序：AFK 任务提交与退出 → 检查资源已停止并保留原结果 → 读取候选与任务差异 → **获授权的独立审查、精简任务** → 新任务提交 → 宿主独立验证新提交 → 更新报告 → `/retro`。[L1][L2][L5]

适用于希望减少同一生成上下文自我审查偏差，或希望先看 review 报告再选择修复项的情况。使用项目内另一具名工作树/分支或明确的后续编码任务，保存原候选；如果再次使用 Sandcastle，需新的任务配置、分支和授权预算，不直接复用已结束运行。普通 Git 分支与新提交足以记录前后状态，无须额外冻结机制。[L2][L3]

代价是新增模型调用/上下文准备、后续授权与时间成本，以及两次候选的证据管理。它仍可放在 retro 前，但不能假称原 AFK 的验证已覆盖精简后提交。若精简无价值，保留原候选并如实记录即可。[U3][L5]

### 不推荐的首轮方式

不默认引入“每次 git commit/push 自动调用模型并改文件”的 hook：上游没有提供这个实现，项目容器原本不允许 push；另起模型会引入预算、取消、失败、暂存差异与资源停机的额外责任。尤其自动修改暂存内容后沿用旧检查，会让提交与验证对象不一致。把阶段明确放在 prompt 或独立任务中，现有普通 Git 和 verifier 已能处理这一问题，无须新增提交 gate。[U7][L3][L4][L5]

## 最小试点与评估

选择一个已经有清楚验收命令、变更范围小、可隔离测试的 issue；首轮避开认证、支付、数据迁移和发布变更。试点前核实实际 runtime/HEAD、镜像、任务起点、模型设置、时间/迭代预算、可读技能与验收工具；这沿用现有准备要求。[L1][L2]

先采用方案 A：只在一轮任务提示中增加一次收尾精简，保留完整插件不安装。审查范围明确到任务起点和候选差异；跨多个任务提交时不使用上游“仅最后一次提交”的默认范围。成功可以是“审查后没有必要精简”，不设删行配额。[U2][U3]

在已有 `docs/issue#N-executing-report.md` 中记录以下普通观察值，不另建 baseline 或强制门槛：

- 具体精简发现、采用/拒绝理由、受影响文件与普通 Git diff 统计；是否减少无关功能、重复实现或不必要依赖。
- 本轮实际耗时；日志可得时记录模型用量/费用，不可得时标记未采集，不从 `captureSessions:false` 推测精确用量。
- 修改后的验收命令、工作目录、退出状态、业务结果与宿主精确候选独立验证；不能只写驱动 exit 0。
- 是否引入回归、需要恢复、触及范围外文件，或因精简耗时无法完成任务；retro 讨论对下一轮的价值。

以上是建议评估方式；已有报告与 retro 的依据、资源停机、执行状态要求来自项目技能，实际试点仍未运行。[L1][L2][L3]

## 失败处理与恢复

同轮发现精简导致检查失败时，在本轮授权和预算内定位并修复；不明确、持续失败、缺能力或超时就停止，保留工作树、日志和进度，不发 COMPLETE。只有能确认是本轮自己的改动时才撤回对应精简；不得用 hard reset、clean 或覆盖用户修改恢复。[L1][L2][L4]

独立后处理失败时，保留原 AFK 候选以及失败候选/工作树；报告区分“原候选验证”和“新候选未通过”。可以选择继续交付原候选，但必须重新确认任务验收与交付授权。若新候选已形成提交，普通新提交修复或撤销特定提交即可表达恢复；不强制改写历史。[L1][L2][L5]

失败、取消或阻塞后仍执行一次宿主 retro，先检查资源停止记录，再讨论本轮的生成、精简、检查与恢复。新增环境建议仍写入报告并标明未实施，不因本次研究自动改动技能或运行时。[L1]

## 后续实施点与三端影响

若决定试点，后续工作主要是任务专用技能输入与 prompt、候选验证与执行报告；首轮不需要修改 `common.mts`、supervisor、git-policy 或认证设置。试点有效后才考虑将这条收尾约定写入 `yuweiju-afk` 技能，并同步 runtime 文档；没有证据时不增加配置字段或自动阶段。[L1][L2][L3]

本次新增研究与流程文档，对 Java 后端、Vue 管理端、微信小程序均无行为、API 或数据库修改。未来精简按实际 issue 读取对应端规范并追踪消费者：后端保持 Controller/Service/Mapper 职责、认证/归属与事务；管理端保持 API 类型、路由/状态和交互；小程序区分手写源码与编译产物。任何接口/schema 精简都必须核对三端消费者、兼容与回退，不能以少写代码省略这项分析。[L0][U2]

## 尚未验证

- 未运行 Ponytail，也未验证容器中技能自动发现、命令解析、完整插件生命周期 hooks 或其状态写入是否兼容本项目；上述兼容判断是源码推论。
- 未用本项目 issue 做前后比较，不能证明代码质量、成本或耗时净收益；同一模型的收尾审查仍可能漏掉自己的错误。
- 未确认远端最新 main 与所有本地 runtime 的同步状态；protected clone HEAD 已读取，issue13 来源只完成源码读取。
- 未执行本项目三端业务测试、Docker AFK 或候选 verifier；本轮文档检查只能证明文档链接等结果。

## 来源

上游固定版本：

- [U1：README](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/README.md)。GitHub 浏览页面核实项目，完整正文另由 raw 下载；性能结论视为作者报告。
- [U2：核心 ponytail 技能](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/skills/ponytail/SKILL.md)。
- [U3：ponytail-review 技能](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/skills/ponytail-review/SKILL.md)及[命令适配](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/commands/ponytail-review.toml)。
- [U4：ponytail-audit 技能](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/skills/ponytail-audit/SKILL.md)。
- [U5：ponytail 命令适配](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/commands/ponytail.toml)。
- [U6：Codex plugin manifest](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/.codex-plugin/plugin.json)、[package.json](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/package.json)。
- [U7：Claude/Codex hooks 配置](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/hooks/claude-codex-hooks.json)、[状态实现](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/hooks/ponytail-runtime.js)。
- [U8：激活实现](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/hooks/ponytail-activate.js)、[安装说明](https://github.com/DietrichGebert/ponytail/blob/9b58c1ffb790c075ca32e70a89cf1d80588f4abf/INSTALL.md)。

本地来源（以本次可读文件为准）：

- L0：[协作入口](../AGENTS.md)、[workflow](agents/workflow.md)。
- L1：[yuweiju-afk 技能](../.agents/skills/yuweiju-afk/SKILL.md)。
- L2：[runtime 指引](../.agents/skills/yuweiju-afk/references/runtime.md)。
- L3：初研读取的 protected clone 文件为 `.scratch/sandcastle-git-safety/runtime/.sandcastle/{common.mts,business-worker.mts,main.ts,task-config.mts}`，HEAD 如前文；这些本机历史路径不属于 fresh-clone 文档依赖。
- L4：[Git 防护说明](../.sandcastle/README.md)、[git-policy.mjs](../.sandcastle/git-policy.mjs)。
- L5：[独立验证指引](../.sandcastle/verification.md)。
- L6：初研读取的 issue13 来源文件为 `.scratch/issue13-environment-improvements/.sandcastle/{verify-task.py,task-config.mts}`。这些本机历史路径不是 fresh-clone 依赖；稳定流程入口为 L1/L2/L4/L5。
