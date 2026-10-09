# Issue #7 复盘改进结果

2026-10-10（Asia/Shanghai）。依据用户要求实施根目录 `docs/yuweiju-issue#7-retro.md` 的建议；该原始文档是用户保留的未提交材料，本轮没有改写其历史状态。方案见 [改进方案](issue7-retro-improvement-plan.md)。

## 实际位置与范围

根目录仍为用户的 `codex/issue-4-refimg-scripts`，保留原有未提交文件和 PLANS.md 删除状态。维护工作树：`E:\Learning Files\yuweiju-takeout\.scratch\issue7-retro-improve`；分支 `codex/issue7-retro-improve-20261010`，从已确认 `origin/main=815b5e9` 开始。实现提交为 `165ad42`、`9637011`；后续报告提交只增加本报告，不改变被测源码。

仅修改协作技能、Sandcastle 工具和检查配置；后端、管理端、小程序业务源码、接口、schema和原库均未改，三端无需同步业务代码。默认一轮/30分钟、idle/grace、模型、专属认证配置和Git防护保留。没有新增hash、冻结contract、baseline、CI或强制合并gate。

## 逐项核对与改动

| 建议项 | 现状核对、实施与验证 | 状态 |
| --- | --- | --- |
| 启动前信息与授权 | AFK 自动读issue/依赖/当前实现；新增 `references/startup.md` 五项交接，沿用会话已知授权，只收集真实缺项。列候选/真实验收能力、服务与GitHub动作、预算和恢复；真实凭据只在本人配置入口输入 | 已实施；技能场景验证通过 |
| 实际环境检查 | #13 已有 `networks` 双网络接入和非登录shell工具指导，跳过重写。新增 `preflight.py`：实际镜像/网络下检查工具、Git元数据、防护、DNS/HTTPS和显式隔离端口；认证可只读检查且输出丢弃。business-worker在编码预算/轮次开始前自动预检 | 已实施；五项真实无模型场景通过 |
| 统一验证入口 | #13 已有Windows/WSL参数转换、候选快照和精确提交 verifier，复用。补临时 `check.sh` 写入UTF-8/LF、独立 `verificationMs`、逐项passed/failed/not-performed。实测发现wslpath会返回Docker临时bind别名，修为转换驱动器根再拼后缀 | 已实施；Windows失败协议与最终候选验证通过 |
| 总预算与候选收尾 | 保持 `totalMs` 所有轮次共享、含安装/检查/收尾；增加预算记录、`closingMs` 提醒文件、prompt绝对deadline和候选保存要求。独立验证预算另设。超时保留aborted、result=null及候选，无法因COMPLETE改成成功 | 已实施；20秒截止场景通过，正式预算未扩大 |
| 逐轮证据 | `iteration-agent.mjs` 在正常退出前保存，5秒采样作为取消备份；`save-iteration.py` 保存选定脱敏文本、HEAD/status、退出/信号/完成文本、跳过原因。resource/result给宿主证据位置。下一轮继承Git提交并按需读证据，不自动回填ignored文件 | 已实施；两轮真实工作树重建后日志仍可读 |
| 人工验收准备 | 新增 `references/acceptance.md`：验证通过后按预授权自动准备服务，检查端口/候选/前端/代理/后端/受保护请求，记录所有权、停止入口、保留期限；一页反馈同时列隔离与真实项。AFK入口已接入该步骤 | 已实施流程；本轮未启动业务人工服务或做真实验收 |
| 验收后交付技能 | 新增 `yuweiju-deliver`：最终main集成/受影响检查、精确候选、唯一PR、超时先读、合并/关闭远端复核、服务处理。草稿保持draft，Release/部署分别确认具体目标；旧权限持续有效，反馈不是新增权限 | 已实施；五个决策场景通过，GitHub写入未执行 |
| 复盘改进技能 | 新增 `yuweiju-retro-improve`：已选条目先核对跳过、项目内隔离、逐项验证。显式新聊天请求才查create_thread；不可用给交接并等待位置决定，不用子代理冒充聊天。不继承业务部署权限，不递归改造 | 已实施；六个决策场景通过；本轮未请求新聊天 |
| 检查反馈和导航 | 根package增加 `check:preflight/check:config/check:evidence`；evidence普通测试接入现有 `checks.example.json` runtime组，宿主Docker检查继续独立入口。修runtime旧checkout导航，详细流程按需读取，根AGENTS没有增加常驻长流程 | 已实施；入口、类型、链接及精确快照验证通过 |

失败后监督器继续停止，不自动重启。启动前可以收集具名技术恢复的显式授权；宿主续跑必须从旧result/resource扣除实际耗时和轮数，未知额度不按零处理，耗尽时需要新增明确授权。取消、认证失败、越权、未知数据副作用和归属不明不能使用技术恢复许可。本轮没有实施自动重启机制。

## 验证结果与来源

以下路径以维护工作树为根；完整证据留在 `.scratch/`，未承诺随fresh clone提供。脚本工作目录均为该工作树或其生成的具名fixture/snapshot；Linux使用Ubuntu WSL、Node22.23.3，容器为非root/C.UTF-8，不挂真实认证、不启动真实编码Agent、不访问原业务库。

| 命令/场景 | 实际结果 | 证据 |
| --- | --- | --- |
| `npm run check:types` | exit0 | 最终快照 `check-0.log` |
| `npm run check:git-policy` | exit0，6/6 | 最终快照 `check-0.log` |
| `python3 .sandcastle/verify-git-guard.py` | exit0，57cases，containerStopped=true | `.scratch/git-guard-verification/wrapper-verdict.json` |
| `npm run check:lifecycle` | exit0，9cases；退出7、不完整、COMPLETE、取消、20秒截止、SIGINT/SIGTERM、idle、缺提交；停止及提交/未提交候选保留 | `.scratch/lifecycle-verification/lifecycle-verdict.json`，fixture `run-hkxa57k7` |
| `python3 .sandcastle/verify-preflight.py` | exit0，5cases：缺出口、持续网络失败、实际双网络+Redis、空认证、探针期间取消；取消exit130且停机记录保存 | `.scratch/preflight-verification/1791569005362387918/verdict.json` |
| `python3 .sandcastle/run-config-fixture.py` | exit0，两轮/两容器；第一轮干净工作树被移除、第二轮从上轮提交重建，原ignored日志缺失但宿主证据仍在；缓存复用、exit/COMPLETE分别记录；随后5项独立检查通过 | `.scratch/config-rebuild-verification/` 的config-fixture-result、resources、iterations及task-review记录 |
| `npm run check:evidence` | exit0，3tests：Basic/Bearer及含空格/转义秘密、日志消失后恢复、认证/二进制/路径/大小边界 | 最终快照 `check-0.log` |
| Windows `verify-task.ps1` 失败夹具 | verifier exit7，驱动非零；首项passed、第二项failed、第三项not-performed；stopped=true；未创建第三项标记。totalMs=1而verificationMs=15000生效；check.sh无CR | `.scratch/windows-verifier/task-review-1791569131381436702/` |
| Windows最终候选 `9637011` 的统一verifier | exit0，三组全部passed，stopped=true、authMounted/modelCalled=false；npm干净安装、类型/policy/evidence、8个Python文件编译、8个入口文档链接均通过。totalMs=1、verificationMs=180000 | `.scratch/final-candidate-verification/task-review-1791569306141490817/resources.json`及check-0/1/2.log |
| PowerShell parser / whitespace | parser通过；`git diff 815b5e9 HEAD --check` exit0 | `.sandcastle/verify-task.ps1`，Git检查 |
| 三个技能应用场景 | AFK4、deliver5、retro-improve6个只读决策场景。AFK旧规则已有超时/权限保护，保留；发现并补复盘触发范围、剩余额度记账、main工具来源、草稿状态和新聊天降级边界 | 本聊天技能测试子代理记录；只读场景不等于真实外部交付演练 |

独立只读代码审查发现并修复两个P1：预检取消时空network.json导致最终记录丢失；Basic Authorization及含空格password漏脱敏。对应普通测试和真实取消场景通过，随后复核未发现新的具体缺陷。

首次lifecycle失败来自旧夹具的10秒idle先于20秒deadline触发。只将deadline场景idle改为60秒，总截止仍20秒；正式默认不变。一次重跑在修正落入夹具前启动，保留其相同失败。最初完整仓库的两轮配置夹具30秒超时，未把它记成成功；随后复用lifecycle的小型真实Git准备方式，通过共享 `fixture_repo.py` 保持原30秒夹具预算并完成两轮。完整业务仓库的真实编码耗时没有在本轮测试。

2026-10-10 GitHub只读查询：main `815b5e9` 的Actions workflows=0、check-runs=0、commit statuses=0（aggregate state=pending）。不能从零条记录推断仓库外服务不存在；原始简表在 `.scratch/github-check-discovery.json`。本轮提供普通可重复触发入口，没有新增CI或合并要求。

## 未执行项与限制

- 未启动真实AFK/模型推理，未验证真实认证成功或微信；HTTP4xx只说明服务可达。业务源码未变，原#7后端50项/管理端3项及业务探针结果作为历史来源，本轮不重新声称它们已执行。
- 人工服务准备、验收后交付和显式新聊天交接已落实为技能/参考步骤并做决策测试；实际“启动业务服务→本人真实验收→GitHub交付”及真实create_thread调用未演练。本任务不授权这些外部动作，也没有需要用户验收的业务候选。
- 没有自动重启；故障后的具名恢复由已有授权下的宿主按剩余额度执行。本轮不改变取消与认证安全边界。
- 截止提示不保证Agent读取或完成提交。取消/SIGKILL前最后5秒或未完成写入可能缺失；超限/不可读文本有明确skipped记录。脱敏处理常见字段，不能保证任意未命名秘密安全，源日志仍须避免敏感正文。安装/编码/检查阶段耗时由进度记录，包装器只记录真实开始/结束，不猜测阶段占比。
- 本轮测试创建的具名容器已停止、专属Redis和网络已移除，证据/fixture/候选及卷保留，其他任务资源不清理。测试并非原库或生产部署演练。
- 本地改动及实现提交已可审查；尚未推送、创建维护PR、合并main或部署。本任务没有授予这些GitHub/发布动作，根目录旧AFK技能没有被覆盖；使用新能力应选择本维护分支或经授权集成后的checkout。

回退使用普通Git恢复/撤销本维护提交；保留用户修改、证据和既有防护。本轮没有业务数据迁移或需回滚的原库写入。
