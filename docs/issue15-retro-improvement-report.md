# Issue15 Loop 复盘改进实施报告

2026-10-13，当前聊天落实用户要求的 [执行报告 retro](issue%2315-executing-report.md) 建议，使用 yuweiju-retro-improve 和 writing-for-agents。范围、三端影响与回退见 [维护方案](issue15-retro-improvement-plan.md)。

实施位置：项目 `.scratch/issue15-retro-improve-20261013`，分支 `codex/issue15-retro-improve-20261013`，起点为已通过 GitHub REST GET 确认的 main `5d90ea32723d92e4b1f56230828e77b8742f83c7`。根用户分支 ecd2029 和修改保留；旧 Ponytail runtime fcf5d28 没有被覆盖。实施完成后用户明确授权本维护任务提交、推送、使用 pr 技能创建 PR 并合并；最终远端状态另记项目内交付记录，不继承业务票的部署权限。

| 原复盘条目 | 本轮结果 |
| --- | --- |
| 宿主输出/退出与资源丢失 | launch 新建独立 host-* 记录，保存脱敏 stdout/stderr、worker exit/signal、来源与最后可读资源快照。资源 workerPid 与 launcher child PID 对应；其他任务停止记录不能使本任务成功。失联/损坏标记 unknown，保留旧可读快照。原5秒采样、停止控制和预算继续保留 |
| 普通 COMPLETE 不匹配 | prepare-input 和真实编码 prompt 共用 closeout-prompt.md；监督器把它置于业务 prompt 后，要求 Ponytail、最终检查、显式暂存及提交后才输出精确独占 promise。离线 input/config 验证共用来源及实际拼接；不改监督器匹配规则 |
| 视觉漏检/截图失败 | 四宽度32消息普通测试接入公共 runtime-check.sh；前置执行，失败立即退出并保留日志/退出表。acceptance 增加官方 size/offset 尺寸检查、合成消息 finally 恢复及人工视觉未执行的标记；不以尺寸通过冒充人工验收 |
| 命令环境 | startup 增加本票身份核实、per-command 身份、PowerShell5 编码和可写 preview 准备步骤。此项是指引改进，未改自动 preflight 的检查行为、global Git 或正式配置 |
| Mockito/JDBC/探针副作用 | startup 已有 doReturn、实际时间映射和身份核对规则，跳过重复实施。准备器生成 preparation-notes 指向这些既有材料；不修改已经验证的业务探针 |
| 技能发现/输出经济 | 当前目录已能发现 retro-improve，复用。技能补“显式命名优先真实文件/映射，确认缺失前定向查找”分支；大输出留本地。根用户输出约束保留，未改全局技能注册 |
| 公共检查/CI | 已接入既有公共 runtime 入口。CI/pre-commit/新 gate 未选；Maven、admin 检查、统一 verifier 和原安全控制保留 |

后端、管理端、小程序产品源码未改；API、schema、业务数据、认证、归属、事务和消费者均无需迁移。没有新增 hash、冻结 contract、baseline 或 gate。

## 验证证据

可移植摘要见 [检查结果](verification-evidence/2026-10-13/issue15-retro/checks.json)，包含命令、退出、场景数及 red/green 摘录；完整日志留在本机 `.scratch/`，不假设远端 clone 拥有这些临时证据。

所有验证产物位于本工作树 `.scratch/`。验证副本从当前维护源码复制，借用旧 runtime 的 package.json 和 Linux node_modules；副本与夹具都在项目内，未编辑依赖来源。WSL 采用已核实 Node v22.23.3 的非登录执行器。检查不调用编码模型、不挂载认证或访问原业务库。

最终脚本检查结果见 `.scratch/verification/1791887157109929842/checks.json`，每项包含 cwd、完整 command、exitCode 和日志路径。验证驱动最终 exit0：red input/host 均 exit1；green input/host/types/lifecycle/config/policy 全部 exit0。原生命周期9场景全部 passed/stopped；配置夹具验证实际 prompt 拼接、两轮承接、环境挂载和独立 verifier，exit0；Git policy 6项通过。最后 docker ps 无运行容器，验证未遗留服务。

已确认的针对性结果：input 普通测试5项 exit0；宿主记录5个场景 exit0，包括非零退出、缺记录、失联、SIGTERM、记录损坏。后者保留最后 stopped 快照但当前仍 unknown。类型检查 exit0。旧 prepare-input + 新测试因缺 closeout 文件失败；旧 launch + 新测试因非零退出后无 host 记录失败，均 exit1。首次 host 夹具目录初始化遗漏不是有效 red，已修正后重新复现；首次 WSL 不能解析 Windows worktree .git 指针，改从根仓库读取旧源码，失败记录保留。没有用这些准备失败证明产品缺陷。

布局证据：`.scratch/layout-verification/1791887001527143391/resources.json`。实际源码渲染命令 exit0；隔离副本撤掉 cs-list 的 width/box-sizing 后，通过真正公共 runtime-check.sh 执行，exit1，退出表仅为 miniapp-customer-service-layout=1，未启动后端。两个具名容器都 stopped=true，网络 none、挂载源码只读、无 auth/model。这证明公共接线和原布局回归，未重跑完整 Maven/admin/SQL，也未重新打开官方模拟器。

技能应用场景人工复核：AFK startup 对缺身份、PowerShell5 中文、只读 preview 三场景；acceptance 对截图失败/尺寸可读、人工视觉未执行、恢复合成消息三场景；retro-improve 对技能未列出但文件存在、宽查询大输出两场景。路径与文本覆盖记录 `.scratch/skill-scenarios.json`（8项）；此记录是场景走查，不宣称独立 Agent 评审或实际重现 PowerShell/EROFS。四个技能/参考的普通相对链接检查 exit0、无缺失（`.scratch/skill-links.json`）。

## 使用、回退和边界

本轮源码在独立工作树可直接审查；历史 Ponytail runtime 未自动获得更新。后续业务任务需明确从本维护源码准备新的项目内 runtime，再执行已有 check:input/check:config/check:lifecycle；材料源码 closeout-prompt.md、launch.check.mts 必须一起带入，现有 overlay 的普通文件复制及 fixture_repo 已包含它们。不启动真实 AFK 试点或重开 #15。

Node launch 记录的是其 worker 的输出和 exit/signal。Windows/WSL 在 Node 启动前失败、整个宿主被强制终止时，外层调用方仍需保留自己的退出信息；无法保证产生 endedAt 或最终 worker exit。已落盘记录继续保留 unknown，不猜测 WSL 根因或补写成功。脱敏覆盖常见命名秘密，不等于完整脱敏证明，源日志仍须不输出业务载荷或凭据，分享前审查。

代码回退可普通 revert 本维护提交；不删除旧证据或卷，不回滚 SQL/Redis。完整公共 runtime、真实模型/认证、官方 SDK、新候选人工验收、WSL 根因修复和部署本轮未执行。远端交付按后续独立维护授权完成，不修改已关闭的业务 #15。
