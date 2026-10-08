# #4 单轮执行摘要

供后续明确授权的单轮使用；本次准备改进不授权编码或启动模型。读取顺序：本摘要 → 根 AGENTS.md、yuweiju-web-vue/AGENTS.md、docs/agents/workflow.md、docs/standards/admin.md → 四个任务文件及邻近必要实现。先定位工具脚本，不重复扫描三端仓库。

## 范围与决定

工作目录为 /home/agent/workspace/yuweiju-web-vue/yuweiju-admin。允许修改范围仅为以下四个文件：
- eslint.config.js：限定 CommonJS/Node 适配作用域，保留其他 TypeScript/Vue 检查。
- scripts/copy-ref-images.cjs：最小输入/输出路径配置、复制错误的非零退出。
- scripts/fix-refimg-imports.cjs：最小扫描根/素材目标配置，保持导入转换语义。
- scripts/refimg-scripts.check.cjs：新增 Node 内置行为测试，不添加业务依赖；.check.cjs 避免 Vitest 误收集。

复制默认源为仓库 yuweiju-web-vue/reference_images；参数命名按邻近脚本风格，测试与报告使用最终实际入口。保留顶层普通文件复制、同名覆盖、无关文件保留、不递归、不删除。导入只转换带 query 的 @refimg/...，query 原样保留，无 query 不改。路径允许显式指定，测试只使用临时夹具，不向真实 src/素材写测试数据。

这是需要继续维护的项目；演示用途不降低兼容性、错误处理、资源关闭和日志脱敏要求。历史代码不符合当前规范时，按规范与已确认行为修正，不把旧模式当作新增代码标准。资料冲突以本轮明确授权、已确认访谈与适用规范核对；无法在本范围解决时停止。

#2 已关闭，旧 Blocked by #2 解除；ready-for-agent 不扩大权限。模型保持 gpt-6.1-sol、medium、Standard，ChatGPT 专用持久认证；额外支出为 0。仅 1 轮，运行含准备/hooks 最多 30 分钟，空闲 10 分钟、完成信号宽限 60 秒；独立复核另限 30 分钟。内部逻辑小步不增加 Sandcastle 轮数或模型调用，不自动续跑。

禁止扩大到页面、API、响应类型、路由、状态、后端、小程序、数据库或业务依赖版本；不得忽略 scripts/降低规则，不恢复 PLANS.md/旧 To-do，不新增 hash、baseline、冻结 contract、gate、pre-commit hook、任务管理系统或自定义缓存。不写 GitHub、不 push/PR/merge/close issue、不操作原库/Redis/模型数据、不发布，不换模型/API、不购买额度或改认证，不安装个人插件/MCP，不启动业务服务。只显式 stage 四个任务文件；保留用户变更，不 reset/clean/自动 stash/git add -A。

## 同轮小步与准确命令

容器 hook 已在此工作目录成功执行 npm ci，直接复用本生命周期的 Linux node_modules，不再次安装。依赖缺失或准备失败时停止；新 worktree、新容器及独立复核仍各自按方案 npm ci，不搬入 Windows node_modules。

1. CommonJS lint 适配：只读两脚本与 ESLint 配置，重现相关错误，完成作用域适配后运行：
   npx --no-install eslint eslint.config.js scripts/copy-ref-images.cjs scripts/fix-refimg-imports.cjs
   node --check scripts/copy-ref-images.cjs
   node --check scripts/fix-refimg-imports.cjs
2. 路径配置：实现最小配置与对应真实临时夹具，新增/更新 .check.cjs；先运行上面的相关 ESLint，再运行：
   npx --no-install eslint scripts/refimg-scripts.check.cjs
   node --test scripts/refimg-scripts.check.cjs
   对尚未实现的退出码/行为例记录“未验证”，不提前宣称全部矩阵通过。
3. 复制失败退出码：用非 root 不可写夹具复现部分复制失败，修复退出状态；再次运行相关 ESLint 和同一 Node 行为测试，补齐下表全部行为例。

每步先修本步实际失败、更新进度，再推进；不等到最终构建才发现脚本错误。所需诊断技能为 diagnosing-bugs；宣称通过前使用 verification-before-completion。纯脚本任务不强行触发 UI/API 技能，不新增测试私有实现细节的测试。

最后从同一管理端工作目录逐项运行，记录实际退出码而非只看驱动 exit 0：
npm run lint
npm run typecheck
npm run test
npm run build
node --test scripts/refimg-scripts.check.cjs

检查与行为矩阵全部通过后，依次显式 stage 本摘要四个授权文件、创建本轮任务提交、用 git show --stat 核对范围、用 git status 核对未提交内容，并把实际提交号写入进度。仅保留未跟踪进度/日志；提交失败则停止并记录阻碍。

独立复核由宿主在新的隔离副本/容器另行执行 npm ci 及上述最终命令；不调用编码代理，不供给模型认证。四项检查通过、两例格式化测试或完成标记均不能代替素材行为验收。构建 chunk 警告单列，不降低阈值或宣称 UI 全覆盖。

## 必须完成的行为矩阵

| 夹具/触发 | 断言 |
| --- | --- |
| 两个顶层文件和子目录 | 两文件内容准确复制，子目录不递归 |
| 同名已有目标及无关文件；再执行 | 同名覆盖、无关文件保留、再次结果一致 |
| 源不存在 | 非零退出，目标无错误写入 |
| 非 root 部分目标不可写 | 实际复制失败导致非零；成功/失败文件分别核对内容 |
| 根层、多层 Vue 中带 query 的 @refimg 导入 | 路径指向配置素材位置，?url/?raw 等 query 原样保留 |
| 无 query、无关导入、非 Vue；再执行 | 内容不变，重复转换不继续改写 |

使用 node:test/node:assert/strict、临时目录和真实子进程；结束只清理本例拥有的夹具。Git 回退不保证找回未跟踪素材，因此每例明确输入/输出位置。只统计输出行或真实 src 中零处替换都不是行为证据。

## 进度、停止与交付

开始执行后即创建 /home/agent/workspace/.sandcastle/issue-4-progress.md，每个逻辑步及针对性检查后更新，结束/失败前最后更新。只记录短摘要：
- 最后完成的逻辑步；当前状态：进行中 / 未完成 / 失败 / 代理报告完成。
- 修改文件及任务提交号；未提交文件位置。
- 已运行命令：工作目录、退出码、实际结果、完整日志位置。
- 尚未验证的矩阵项或检查；阻碍及下一步。
- 独立复核：待宿主执行；人工接受：待维护者审查。

该文件不写只读 /home/agent/task-input，不 stage/commit、不放入被忽略的 logs 目录。保留为未跟踪文件，使已有 Sandcastle 脏 worktree 机制保留它；资源记录和结果记录提供真实 worktree 路径。完整命令日志写同一 worktree 的 .sandcastle/logs/issue-4-*.log，不输出凭据；聊天/最终答复只列相关错误、退出码、文件位置及下一步。宿主现有编排日志/result/resource JSON 保留其来源与完整失败信息。没有产生代理进度时，宿主记录“未产生”，不得猜测逻辑步。

材料/技能缺失、认证/模型/额度异常、安装或范围外依赖/类型/构建故障、越界、需要人工回答或达到时限时停止并报告；只保留 #4 可修复 lint/脚本行为问题在同一轮推进，不循环猜测环境。取消仍经已有 IPC→AbortSignal、finally/close 与资源归属审计；失败保留成果。不得自行重跑、扩轮或恢复运行。

仅全部最终检查和行为矩阵有实际通过证据，且本轮任务提交已存在、范围核对通过时，按正式 prompt 输出完成标记；该状态只表示“代理报告完成”，独立复核通过与人工接受由各自执行者另行记录。失败/未完成不用该标记。进度可在停止后由宿主复制至既有证据/恢复目录，保留原 worktree；下一次人工授权后，才由宿主将所选脱敏恢复摘要供给为静态任务材料，并先核对提交与未提交状态。本文件不会自动加载历史恢复摘要。

## 按需资料位置

- 规格/验收歧义：docs/yuweiju-restore-spec.md 的 B2、Testing Decisions；/home/agent/task-input/issue-4.md（静态正文，旧 npm.cmd 命令已在本摘要转换为 Linux 入口）。
- 已确认边界歧义：/home/agent/task-input/sandcastle-early-grilling.md 的 Q5–Q14；恢复/时限仍按已确认方案，不照搬初研中的 API key/扩轮建议。
- 安装/编排历史问题：/home/agent/task-input/sandcastle-early-research.md，查相关段落即可；环境故障停止交宿主，不探索/重装整个工具链。
- 当前五份仓库/个人技能保持原挂载；上述两份个人技能在 /home/agent/.agents/skills 下，必要引用随目录供给。
- 宿主报告：E:/Learning Files/yuweiju-takeout/docs/sandcastle-executing-report.md；模型容器不挂载原工作区。
- 宿主证据：/home/endercloud/projects/yuweiju-afk-evidence；容器内不假定该宿主路径可访问，使用宿主交付的真实日志/保留路径。

行为测试平台：完整素材验收使用 Linux 的真实权限夹具，独立容器采用非 root 用户；Windows 原生不作为权限行为验收环境，不跳过用例。新增 npm run test:refimg 入口与上述显式 Node 命令等价；npm run test 仍只运行 Vitest。独立复核入口及参数见 .sandcastle/README.md 的 Independent verification。
