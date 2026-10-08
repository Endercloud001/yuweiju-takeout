# #4 编排准备效率改进

2026-10-07（Asia/Shanghai）。只改准备资料与结果展示，不执行 #4、不调用模型。开始时实际 HEAD=812107b622b049ecceff97485173dbff309ad178，分支 codex/afk-setup-issue-4，git status 为空；没有报告之后的新变更或冲突。保留原工作区用户变更。

## 改动与影响

- issue-4-summary.md：四个允许任务文件、已确认行为/禁区、六组行为矩阵、Linux 检查命令、停止条件、时限、规范要求及按需资料位置。部署相同内容到仓库外 input/documents/issue-4-summary.md；原三份静态材料不改。
- prompt.md：先读摘要和必要规范，按 CommonJS lint → 路径配置 → 复制失败退出码/行为验证在同轮推进；每组先跑快速反馈，最后跑四项检查与全部行为例。明确需要继续维护的项目不能因演示用途降低质量或沿用不合规范旧代码。
- 依赖准备：实查 hook 每个新容器已有且仅有一次 npm ci，故保留；prompt 要求复用本生命周期已成功安装的 Linux 依赖。新 worktree/容器及独立复核仍按方案准备，不复用 Windows node_modules、不加缓存/安装判据。
- 进度：未来任务 worktree 中 .sandcastle/issue-4-progress.md 为未跟踪、非忽略文件，记录最后完成步骤、改动、命令/退出状态/完整日志路径、未验证项、阻碍和下一步；不写只读 input、不提交、不恢复 PLANS.md。完整日志在同 worktree .sandcastle/logs/，宿主现有结果/资源记录定位保留目录。
- result-summary.mts 与 worker 输出：完成/未完成/失败分开，独立复核与人工接受显式为 not-performed，输出下一步和文件位置。失败摘要只输出最多 500 字符的首行；原 boundedRun 本地记录保留完整失败文本与日志来源。progressFile 是保留目录中的候选进度路径，不代表旧记录中一定存在该文件；未产生进度时应如实记录。

监督进程、worker 的 IPC/AbortController 与 finally、共享 run、hook、资源归属审计、成果恢复、模型/medium/Standard、认证内容、镜像、依赖版本和一轮/各阶段时限保持现状。三端业务代码/API/schema、业务依赖均未改，没有 #4 修复或任务提交。没有新增 pre-commit、gate、冻结 contract、自定义 hash/baseline 或任务管理系统。

恢复摘要不会自动载入或恢复运行；停止后可由宿主保留到既有 evidence/recovery，下一次人工授权并检查提交/未提交状态后，才供给所选脱敏静态摘要。当前只生成无模型保存验证夹具，没有真实代理进度、独立业务复核或人工接受。

## 验证

验证工作目录为 clone；使用已安装 Sandcastle 0.12.0、tsx 4.23.15、Linux Node v22.23.3 与已有隔离管理端 TypeScript，不重新安装工具或业务依赖。

| 检查 | 结果/来源 |
| --- | --- |
| 摘要与规格人工核对 | 对照 minimal-plan 步骤 7–9/矩阵、访谈 Q5–Q14、静态 issue、B2 和管理端标准；只新增表达/阅读顺序，无新业务决定 |
| 内容及路径检查 | exit 0；source/deployed 摘要内容相同，8593 bytes；无 shell 展开，规范/任务来源存在，npm 命令在实际 package.json 中；efficiency-content-check.json |
| 保留逻辑与依赖比较 | common/launch/main/resource/smoke/install/config/Dockerfile 及根/管理端依赖与旧提交逐字节相同；worker diff 只涉及结果展示 |
| TypeScript | exit 0；NodeNext/noEmit/allowImportingTsExtensions/skipLibCheck，所有 .mts 与 main.ts；efficiency-typecheck.log |
| 无模型进度保存 | exit 0；实际 createSandbox/exec/close，同 Docker provider 的无认证挂载模式；未跟踪进度使 worktree 保留，日志可读，进度复制至既有 recovery 后内容正确；具体容器移除；efficiency-progress-check.json/log |
| 结果格式 | exit 0；使用已保存的真实 smoke complete/incomplete/exit7 记录，检查完成仍需复核/人工接受、未完成不升级、失败/引用标记不升级、失败输出收窄且完整原记录保留；efficiency-result-summary-check.json |
| diff/范围检查 | git diff --check exit 0；仅准备资料和 worker 结果展示，不改取消/清理逻辑 |

所有检查脚本和完整输出保留在 /home/endercloud/projects/yuweiju-afk-evidence/efficiency-*，不新增永久测试门禁。文案使用内容与路径检查，没有为文字编造运行测试。无生命周期/共享取消清理变更，故不重跑完整八场景或安装/认证实验；原八场景证据保留，本轮仅运行必要的进度保存与结果展示检查。

本次验证分支/具体容器/worktree/恢复路径/宿主 PID 见 efficiency-progress-check.json；没有调用 sandbox.run 或业务编码代理，没有认证挂载、登录/注销或模型请求。无模型验证的未跟踪进度夹具与日志留在该专用 worktree，至少保留至维护者审查结束。

## 交付与后续

显式提交本轮准备文件，新的最终准备提交记录于原工作区 docs/sandcastle-executing-report.md 的“编排准备效率改进”段；旧提交保留为历史安装版本。后续正式 #4 应从新准备提交启动，并核对 input 中的摘要与提交版本一致、HEAD/worktree/授权边界，再运行单轮。

改进的实际 AFK 时长、真实模型/额度支持、#4 的 lint 与素材行为验收仍未验证。安装可用、无模型准备验证通过不等于 AFK 任务验收。
