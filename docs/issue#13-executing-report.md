# Issue #13 Sandcastle AFK 执行与独立验证

日期：2026-10-09，Asia/Shanghai。用户授权：使用升级后的 Sandcastle 实施 #13，最多 2 轮、总时限 30 分钟，完成后独立验证并交付。使用 yuweiju-afk、yuweiju-java-backend、verification-before-completion；收尾按 yuweiju-afk 要求执行一次 retro，参考 writing-for-agents。

交付结论：本地候选提交 `b7d35c9f7ff93b0dd9a776c985035221b40a2465` 的自动验收及独立复验通过；维护者随后在同一候选隔离环境完成人工正常登录、小程序下单与管理端查看订单，见末尾人工验收补充。本票故障隔离目标和正常三端链路已获自动/人工证据；金额一致性并未验收通过（既有范围外差异见补充）。AFK 交付当时未合入原工作区，未 push、创建 PR、写入 GitHub、原库操作或发布。后续维护者已授权 PR、retro、推送与最终合并；当前集成验证见 issue13-integration-report.md。

## 起点、预算与资源

- 使用项目内升级运行时 `.scratch/sandcastle-git-safety/runtime`，原准备 HEAD `a5422528ddca3fc2f06cf2528736c49628ae6686`。额外准备提交 `09c2962d30eef79a41f9f2baaefe8d2032c36bc3` 只为编码/独立验证增加显式专用 Docker networks 配置，共 3 文件 3 行增加、2 行删除；类型检查、Python 编译、diff 检查和真实双网络连接检查通过。Git guard、模型、认证及停止控制未改变。
- 分支 `codex/afk-issue-13-20261009`；startCommit 为 `09c2962`；镜像 `sandcastle:yuweiju-dev-git-safe`。模型 gpt-6.1-sol / medium / fast_mode=false；maxIterations=2，totalMs=1800000，idle=600 秒，completion grace=60 秒。
- 编排开始 14:42:48，结束 15:07:24，耗时约 24 分 36 秒；实际 Sandcastle iterations=1，未超预算。代理进度文档把第二次相同源码检查称为“Iteration 2”，那是检查批次，不是第二个编排轮次。没有重启/续跑编码代理。
- supervisor exit 0、completionSignal COMPLETE、一个真实 task commit 三者均确认；资源记录 stopped=true，所有 iteration container 均 stopped=true。保留 worktree 是因为未提交进度文件，不是业务源码未提交。
- 本票专用 MySQL/Redis 仅 internal network、不发布端口；编码/独立构建另有下载/模型通信网络，没有挂载原数据库或原配置。MySQL 数据保留在本项目 `.scratch/issue13-afk-20261009/mysql-linux/disk.img` 的 ext4 文件系统。两个专用数据库容器已正常 stop/rm，两个专用网络已移除；没有清旧卷、prune 或停止其他任务。卸载证据见 `mysql-unmount.log`；镜像保留。
- 宿主本票 ext4 挂载已卸载，loop detach 已请求；当前 `/dev/loop2` 仍显示 AUTOCLEAR=1，待 Docker/WSL 剩余引用释放。没有把此状态写成 loop 设备已完全消失；不强拆其他挂载。

## 改动与三端影响

任务提交共 13 文件，736 行增加、334 行删除：

- 观察服务完整 exposure/click/conversion/log 操作边界隔离 RuntimeException；内部上下文/集合/计数写失败立即停止后续统计，不把失败当成功。读取统计摘要失败仍由其自己的调用方处理。
- 订单 Service 保留真实事务，检查核心写入结果；先写订单/明细并清当前用户购物车，再执行附加转化/评分。核心错误仍返回失败，回滚订单/明细/购物车。地址归属及可售校验在 Service 复用路径成立；金额规则不变。
- 购物车 add/sub/list/clean 委派具名 Service/Mapper；事务从 Controller 归 Service，已有购物车条目也检查可售性。订单 Controller 的未知失败响应隐藏数据库详情。
- 增加两个观察故障单元测试、独立真实存储探针/启动脚本、实施方案及副作用说明。探针不增加业务依赖或 gate/hash/contract/baseline。

变更文件：`.sandcastle/environment/Issue13Probe.java`、`issue13-check.sh`；`docs/issue13-implementation-plan.md`、`issue13-failure-boundaries.md`；后端 `UserOrderController`、`UserShoppingCartController`、`ShoppingCartMapper`、`AnalysisObservationService`、`ShoppingCartService`、`AnalysisObservationServiceImpl`、`OrdersApplicationServiceImpl`、`ShoppingCartServiceImpl`、`AnalysisObservationFailureTest`。

后端修改不改变 API/schema/公开字段/金额；管理端和小程序继续消费原接口，无需源码改动。管理端仍执行四项工程检查，HTTP 探针覆盖双方读订单；没有用这些结果冒充原生界面验收。源码评审检查了授权/核心错误不在可选 catch 内、Spring 代理、具名 Mapper 和 scoped 数据清理。现有无关历史图片读路径未在本票修改。

## 独立验证（确切候选提交）

独立快照 `.scratch/issue13-afk-20261009/independent/task-review-1791529669579198663/snapshot`，由 Git detached worktree 从 `b7d35c9f7ff93b0dd9a776c985035221b40a2465` 创建。snapshot tracked diff exit 0；无认证挂载、无模型调用、UID/GID 1000、同一 Git-safe 镜像，新容器且不复用代理 Maven/npm 缓存。数据库为本票专用服务，探针每次创建新合成用户/商品/地址/订单及专属 Redis keys，运行结束清理自己的数据。

命令入口：从 runtime 执行 `SANDCASTLE_EVIDENCE=<本票 independent 路径> python3 .sandcastle/verify-task.py --config <本票 config.json> --commit b7d35c9f7ff93b0dd9a776c985035221b40a2465`，exit 0，stopped=true。

| 工作目录 | 命令 | 独立结果 |
| --- | --- | --- |
| /workspace | mvn -B -f yuweiju-backend/pom.xml package | exit 0；21 tests，0 failures/errors/skips；BUILD SUCCESS |
| /workspace | bash .sandcastle/environment/issue13-check.sh | exit 0；43 个真实 Spring/MyBatis/MySQL/Redis 场景 PASS |
| /workspace/yuweiju-web-vue/yuweiju-admin | npm ci；npm run lint/typecheck/test/build | 整组 exit 0；2 Vitest tests 通过，构建通过 |
| 宿主 runtime / snapshot | git diff --check 09c2962 b7d35c9；snapshot git diff --exit-code --stat | exit 0；候选无空白问题，独立快照 tracked 文件未变化 |

43 场景包含：21 个曝光/点击/转化写前/写后/expiry 失败与同事件重试；8 个 HTTP 提交降级/无上下文场景；7 个购物车降级场景；3 个真实 SQL trigger 故障（订单 insert、明细 insert、购物车 delete）；身份/地址归属/可售、已有风险 Mapper 故障继续隔离、HTTP 正常 roundtrip 及实际 AOP/存储检查。

核心回滚以完整订单/明细/购物车行快照对比，并确认真实触发器异常到达目标 Mapper，不只比较订单计数。降级成功后实际订单金额 36、明细 2×18，当前用户购物车空而另一个用户购物车保留。HTTP 覆盖 add/sub/list、submit、用户明细、管理员详情。fixture JWT 只证明接口认证/归属，不证明密码登录。

复验后专用库只读检查：issue13 synthetic users=0、orders=0、shopping_cart=1（原合成基准行保留）、issue13 triggers=0。保留原始查询输出在 cleanup-check.txt。

## 四项验收与限制

1. Redis GET/记录失败不阻断下单/购物车，真实订单和明细正确、用户购物车隔离：独立自动验收通过。
2. 核心订单/明细/清车 SQL 失败仍失败并完整回滚：真实 Spring 代理和 MySQL 验收通过，非 Mockito 事务证明。
3. 无推荐上下文正常、权限/地址归属/不可售仍拒绝、已隔离评分 Mapper 故障不阻断订单：独立验收通过。
4. Redis 已写后失败逐项记录副作用与重试行为：独立验收通过。JSON 文件 `probe-results-939933801.json` 含实际上下文、set membership、counter 与 TTL；代码没有自动重试，click/conversion 顺序同事件由上下文去重。上下文提前写成功可能导致随后 metric 缺失；set/counter 部分成功可能留下 TTL=-1；exposure 重试可能重复计数。没有 Redis 回滚、持久补偿或 exactly-once 保证。

事务 commit 在可选统计之后，commit 失败/响应丢失仍可能留下 Redis 副作用；probe 未模拟 commit acknowledgement loss。并发 read-modify-write、上下文过期/替换、业务重试得到新订单 ID 仍存在统计遗漏/重复风险。保留这些局限，不引入 outbox/队列或清理已有缓存。

AFK 收尾时未验证的原生小程序正常首页/点餐/结算、管理端实际订单页面及正常账号密码登录，已由维护者后续人工测试补充（末尾记录）。人工测试未逐项报告增减数量、越权或故障注入操作，这些相关边界仍以先前自动证据为准。远端 AI/地图/OSS、真实微信身份交换、训练预测效果及正式发布仍未验证；本次微信为 dev 模拟身份、支付为模拟支付。

既有依赖审计输出 23 项 vulnerabilities 和大 chunk 提示，本票未变更依赖，四项检查实际通过；这些输出不证明依赖风险已解决。

## 故障、来源与恢复

本轮首次 GitHub GraphQL 请求 TLS timeout；按项目文档单次 GODEBUG 兼容重试仍失败，随后 REST issue/dependency 读取成功，保存 issue.json/dependencies.json/fetch-record.json。正文 comments=0。#2 已关闭，ready-for-agent 不扩大授权。两份历史 harness 输出已清理，仅剩源码与总结；未恢复、未对原库补证。

宿主 launch 初次非登录 shell 找不到 node（exit127），未启动 supervisor/worker。使用实际绝对 Node 路径修正调用并保留 invocation-error 日志；之后仅启动一次 supervisor。代理曾在 login shell 丢失 Maven PATH（exit127），同轮改非登录 shell后检查通过；找错 GlobalExceptionHandler 路径、jar 尚未生成的 readiness 失败均记录并纠正，没有跳过验收或扩大轮次。

路径：本票配置/输入/宿主日志在 `.scratch/issue13-afk-20261009/`；AFK result/resource/agent log 在 `evidence/`；代理进度及两批检查证据保留于 runtime 的 `.sandcastle/worktrees/codex-afk-issue-13-20261009/`；独立 runner/environment/check-0/1/2.log、exit-status.tsv、resources.json 在上面的 independent 目录。结果及恢复不依赖旧 #4 目录。

交付补丁 `.scratch/issue13-afk-20261009/issue13.patch` 只包含候选业务提交，不含 runtime 准备提交、进度、日志、编译输出、认证或宿主已有修改。使用 runtime 中任务分支可查看完整提交。与主线整合时先核对现有相邻票（特别是订单共享文件）的最新提交，正常合并/冲突处理，不强制覆盖；AFK 实施阶段没有授权执行远端集成；后续维护者已明确授权独立分支推送、PR 和最终合并。

人工 review：先阅读 `docs/issue13-failure-boundaries.md` 的真实 Redis 残留和本报告限制；查看 13 文件 diff；在授权隔离环境完成客户端正常交互/正式登录；最后决定本地整合和 Issue/PR 操作。源代码回退不撤销已创建订单、SQL 自增、Redis 或外部副作用。

## Retro：按严重程度的环境改进建议

已先核对本轮资源停止记录，再读代理日志/进度/result、候选 diff 和独立结果。项目 runtime 的 package.json 已有 types、git-policy、git-guard、lifecycle 检查；业务验收仍使用既有 Maven/npm 与本票普通探针，没有增加新 gate。

- shell 工具路径：宿主和代理各发生一次 PATH 造成 exit127。建议 AFK 启动导航示例直接使用已核实的绝对 Node 路径，并明确 Java/Maven 检查用非登录容器 shell。影响：减少无业务价值的失败和纠正。状态：本票调用已修正；未扩大修改共享技能/运行时文档。
- 区分编排轮次与验证批次：实际 result.iterations=1，但代理进度把复跑 checks 写作 Iteration 2。建议 prompt 统一使用“检查批次”，编排轮数只从 result/resource 读取。影响：预算报告可核对。状态：本报告已纠正术语，建议未写入共享配置。
- 真实存储接入：旧 provider 和独立 verifier 没有显式网络配置，已有 DB 探针不能直接使用。影响：即使代码可写，也无法证明本票要求的真实事务。状态：本票准备提交 09c2962 已完成最小 networks 接入并通过类型/语法/真实连接与完整运行；未重构生命周期/防护。
- 故障诊断证据：观察服务降级日志目前只输出 operation 和异常类名，便于脱敏但缺少具体调用上下文/底层原因。建议后续在保留脱敏前提下设计可定位的 user/order 上下文与安全 cause 记录；不记录原始敏感 Redis/HTTP 正文。影响：生产故障排查更可追踪。状态：候选中尚未修改，业务验收已独立通过；作为后续维护建议，不冒称已完成。

没有恢复历史日志、修改全局 Git、增加 hash/冻结 contract/baseline/gate 或绕过 Git guard。


## 人工验收补充（2026-10-09）

来源：维护者在本聊天提供四项人工测试报告及六张截图。验收环境为 `.scratch/issue13-live-20261009/`，业务源码来自已独立验证的候选 b7d35c9；未换成原工作区旧代码、未访问原数据库。以下人工操作由维护者执行，不冒称为代理自动UI操作。

| 人工测试 | 用户报告与截图证据 | 结论 |
| --- | --- | --- |
| 后端正常访问 | 8080 `/user/shop/status` 显示 code=1、success=true、data=1 | 通过 |
| 管理端正常账号密码登录 | 输入提供的隔离测试账号密码后进入 `/dashboard` | 通过；真实登录页面操作，账号属于合成夹具 |
| 原生小程序正常下单 | 首页营业中、18元测试餐已加入购物车；结算页有测试地址；随后显示“下单成功” | 正常点餐、结算和模拟支付链路通过，不证明生产微信认证/真实支付 |
| 管理端查看本次新订单 | `/order` 显示订单号1791543317096、测试餐×1、订单金额19.00 | 正常跨端订单可见性通过 |

保存的原始附件在 [人工截图目录](verification-evidence/2026-10-09/issue13-human/source.json)：[后端](verification-evidence/2026-10-09/issue13-human/shop-status.png)、[小程序首页](verification-evidence/2026-10-09/issue13-human/miniapp-menu.png)、[结算](verification-evidence/2026-10-09/issue13-human/miniapp-checkout.png)、[成功页](verification-evidence/2026-10-09/issue13-human/miniapp-success.png)、[管理端订单](verification-evidence/2026-10-09/issue13-human/admin-order.png)。工作台截图含浏览器保存密码弹窗，仅在聊天中核对，不复制进文档附件；文档不记录密码。

代理随后对本次具名隔离MySQL做只读复核（docker exec yuweiju-issue13-live-mysql，sandcastle_fixture，exit0）：订单ID40/订单号1791543317096，用户900003；订单amount=19.00、pack_amount=1、status=2、pay_status=1；明细数量1、单价18.00；该用户shopping_cart行数0。这证明本次实际订单/明细存在且购物车已清空。未新增订单、修改状态或清理验收数据；服务继续运行供维护者使用。

额外观察：小程序结算展示18元商品+1元打包费+6元配送费=25元，而实际订单与管理端为19元。#13明确排除调整金额规则，因此记录为既有范围外差异，不在本票改收费、不把该结果写成金额一致性通过。截图同时出现客户端/服务端送达时间口径差异，未在故障隔离票中改配送时间。

结合此前43个真实存储故障场景和此次人工正常三端链路，可以将#13故障隔离实现交付为自动/人工验收通过；远端合并、Issue关闭及发布仍按后续授权执行，本轮仅更新本地证据与报告。
