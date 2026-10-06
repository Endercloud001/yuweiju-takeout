# 余味居最小验证记录与维护路线复评

> 2026-10-06 维护更新：本文保留当时方案/调查/验证事实，不作为现行强制规则。PLANS 已退出流程；当前协作与技术规范见仓库 `docs/agents/workflow.md`、`docs/standards/`。旧工具规则和冲突 Code Style 已退役；历史来源名称不再代表执行要求。
> 原始测试输出、截图与缓存已按维护者决定删除，附件名称仅为历史来源；源码脚本仅在本机原目录保留，未纳入本次文档迁移提交，含写入/认证调试/训练副作用，未经环境隔离和任务授权不得重跑。输出清理不改变当时失败、数据写入或未验证结论。


验证日期：2026-10-05，Asia/Shanghai。范围：原库、现有三端、点餐履约、人工客服、DeepSeek、智能故障隔离、历史图片、训练预测案例。结论：**后端和管理端可构建；服务端口开启后的补验确认，现有小程序编译产物能在模拟器中运行，并与管理端跑通一条点餐履约、人工客服链路。真实推荐与上游失败提示有运行证据，但无效模型内容仍误显示推荐，下单前后金额也不一致。推荐保留技术栈的局部重构；不据此授权实施或宣称全面验收。**

报告保留首次验证记录；“端口关闭”“尚未验证”“服务已停止”等首次状态，以文末前的“服务端口开启后的三端补验”及更新后的未验证清单为准。

## 约束、资料与证据等级

已读取根及三端 AGENTS.md、`docs/agents/domain.md`、`docs/yuweiju-judgement-grilling.md`、GLOSSARY.md、ADR 0001/0002、后端 `.trae/rules/rules.md`、管理端 `.trae/rules/project_rules.md`，并核对当前源码。使用 verification-before-completion；故障按 diagnosing-bugs 建立可执行复现、缩小范围和定位，本轮不修代码。

- 后端保留 Controller/Service/Mapper 常规分层，历史图片按 ADR 0002 保留原图，缺失时展示兜底；智能失败不阻塞点餐和订单查询，DeepSeek 失败只提示重试。
- 直接访问现有 `localhost:3306/yuweiju`、现有 Redis。未清表、执行 DDL、迁移 schema、初始化数据库、批量生成订单或修改业务源码、配置、认证措施。正常演示写入与训练产生结果记录；具体影响见后文。
- 本次是验证，不修改接口、行为或架构，因此没有新增实施 ExecPlan。根 PLANS.md 在开始时已被删除；保留该状态。COBOL 规则缺少实际流程依据，本轮不假定有 COBOL 迁移。
- 没有新增 hash、冻结 contract、baseline、gate、用户封禁或远端 Issue，没有支付或发布操作。
- **真实运行**：现有 jar/API/数据库/模型文件/浏览器/WebSocket。**故障注入**：独立验证进程中的原服务，替换指定依赖，原库写入事务回滚。**内存案例**：原方法配合内存商品/明细，不改原库。**静态核查**：不能替代客户端运行。
- HTTP `200` 不等于业务成功，需同时查看 `code`、响应数据、实际结果和服务日志；空结果/跳过训练不记为完成训练。
- HTTP 和浏览器验证使用原库存在的管理员 1、用户 17/18，依据现有本地 JWT 配置生成短期测试 token；请求仍经过现有验签、黑名单与归属检查。**没有验证管理员密码登录或真实微信授权登录**，不把测试 token 当成登录链路证据。token 不保存到报告、截图或脚本。

证据目录：verification-evidence/2026-10-05（历史附件已清理）。脚本属于验证材料，不是业务代码或新门禁。部分脚本包含正常演示写入，重新执行会再次新增数据；不要把它们当成无副作用的健康检查。

## 构建、检查与运行命令

下表命令在对应工作目录执行，完整结果保留到所列文件。Windows 使用 `npm.cmd`/`npx.cmd`。开始时 MySQL 3306、Redis 6379 已监听，8080/5173 无现有服务。

| 工作目录 | 命令 | 结果与证据 | 范围/未验证项 |
| --- | --- | --- | --- |
| `yuweiju-backend` | `mvn -B package` | exit 0，BUILD SUCCESS，6 类共 19 tests，0 failures/errors/skipped；backend-package.log（历史附件已清理） | 单元/Mockito 测试不等于原库事务和完整三端回归 |
| 管理端 `yuweiju-web-vue/yuweiju-admin` | `npm.cmd run build` | exit 0，vue-tsc + Vite 产出 dist；admin-build.log（历史附件已清理） | 大于 500 kB chunk 警告保留，不把警告记为构建失败 |
| 同上 | `npm.cmd run typecheck` | exit 0；admin-typecheck.log（历史附件已清理） | 独立类型检查 |
| 同上 | `npm.cmd run test` | exit 0，1 文件、2 格式化测试；admin-test.log（历史附件已清理） | 无订单/客服/UI 测试覆盖证明 |
| 同上 | `npm.cmd run lint` | exit 1，22 errors；admin-lint.log（历史附件已清理） | 不符合现有管理端检查要求，本轮未修 |
| 同上 | `npx.cmd eslint scripts/copy-ref-images.cjs` | exit 1，单文件稳定复现 13 errors；admin-lint-repro.log（历史附件已清理） | 缩小 lint 故障范围 |
| 同上 | `npx.cmd eslint src` | exit 0；admin-lint-src.log（历史附件已清理） | 支持错误集中在 scripts，不代替全项目 lint |
| 后端 | `java -jar target/proj-boot-1.0-SNAPSHOT.jar`，执行两次 | 两次 exit 1，Tomcat loopback 建立失败；backend-start.log（历史附件已清理）、backend-start-repro.log（历史附件已清理） | 默认启动失败，不能写成开箱即用 |
| 后端 | `java '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp' -jar target/proj-boot-1.0-SNAPSHOT.jar` | Started App，8080，随后真实 API 可达；backend-shorttmp.log（历史附件已清理） | 仅验证进程参数；未改项目配置 |
| 管理端 | `npm.cmd run dev -- --host 127.0.0.1` | Vite ready，5173；admin-runtime.log（历史附件已清理） | 本地 Vite 开发运行，不是生产部署 |
| 根目录 | `& 'E:\微信web开发者工具\cli.bat' --help` / `auto --help` | CLI 可调用；wechat-help.log（历史附件已清理） | 工具存在不证明项目运行 |
| 根目录 | `& 'E:\微信web开发者工具\cli.bat' auto --project 'E:\Learning Files\yuweiju-takeout\yuweiju-weixin-miniapp' --auto-port 9420` | 提示 IDE service port disabled；wechat-auto.log（历史附件已清理） | 未开启服务端口/代答安全设置，编译与模拟器运行未验证 |
| 根目录 | Python 读取 app.json、检查页面四类文件，对所有 JS 执行 `node --check <file>` | 13 pages 文件齐全，36 JS 无语法错误；miniapp-static.json（历史附件已清理） | 无 package.json、.vue 源工程入口；不能证明 uni-app 重建或微信编译通过 |

环境使用现有 Microsoft OpenJDK 21.0.10、Maven 3.9.10、本机 Node/npm、Vite 7.3.1。具体版本输出补记于 environment.log（历史附件已清理）。没有安装或升级框架、数据库或开发者工具。

### 启动与 lint 故障定位

启动最小复现是原 jar 默认命令，失败堆栈为 `Tomcat → WindowsSelectorImpl → PipeImpl → UnixDomainSockets.connect → Invalid argument: connect`。先排查 JDK 管道、系统 loopback 与端口冲突；8080 没有占用。用 `javap -c -p sun.nio.ch.PipeImpl` 和 `sun.nio.ch.WindowsSelectorImpl` 检查本机实现，证据见 jdk-pipe.txt（历史附件已清理）、jdk-selector.txt（历史附件已清理）。

仅改变 Unix domain 临时目录后同一 jar 启动，因此已定位到默认临时目录相关的 socket 建立问题；没有证明是项目业务缺陷，也没有进一步确定 Windows 临时目录/驱动的底层原因。尝试 `-Djdk.nio.channels.Pipe.disableUnixDomainSockets=true` 未解决；首次未加引号被 PowerShell 错误拆参，属于验证命令错误，随后加引号重试。尝试本机 Temurin 21.0.7 时 8080 已由成功进程占用，不能用该次失败比较两种 JDK。相关尝试保留在 `backend-jvm.log`、`backend-temurin.log`，不作为业务失败证据。

lint 两文件分别为 `scripts/copy-ref-images.cjs` 与 `scripts/fix-refimg-imports.cjs`。当前 eslint.config.js 把 TypeScript 推荐规则应用于 `.cjs`，没有相应 Node globals 配置；报错包含 require-imports、require/__dirname/process/console 未定义。单脚本复现与 src 单独通过支持此定位；没有改规则、关闭检查或修脚本。

## 逐项业务验证

主命令（根目录）：`python docs/verification-evidence/2026-10-05/verify_api.py`，exit 0。每次请求的 method/path/status/code/data 记录于 api-results.json（历史附件已清理），简要过程见 api-summary.log（历史附件已清理）。脚本退出 0 仅表示执行完毕，下表分别判断业务结果。

| 案例 | 实际命令/入口 | 结果与证据 | 判定和限制 |
| --- | --- | --- | --- |
| 未登录访问 | `GET /admin/order/conditionSearch?page=1&pageSize=1` 不带 token | 401，code 0 | 认证边界拒绝访问 |
| 原订单分页 | 同上，带管理员测试 token | code 1，初始 total 953 | 原 schema 可查询；原分页路径含图片修补副作用，不能称纯只读 |
| 购物车 | `POST /user/shoppingCart/add`，dishId 1；`GET /user/shoppingCart/list` | code 1；用户 17 开始无购物车记录 | 只加入本次演示商品，未清理其他用户购物车 |
| 下单 | `POST /user/order/submit`，addressBookId 29，用户 17 | 新订单 **969**、金额 39，code 1；created-order.json（历史附件已清理） | 使用已有菜品/地址；正常提交会清理该用户本次购物车，这是现有业务行为，未清表 |
| 模拟支付 | `PUT /user/order/payment`，本次 orderNumber | code 1，mock 支付参数 | 不发生真实收付款 |
| 接单/配送/完成 | `PUT /admin/order/confirm {id:969}` → `/delivery/969` → `/complete/969` | 各 code 1；本人查询 status 5、payStatus 1 | 一条 API 履约链路通过；非小程序点击的三端 E2E |
| 本人/他人订单查询 | `GET /user/order/orderDetail/969`，用户 17/18 | 本人 code 1，用户 18 code 0 | 归属隔离通过；HTTP 200 的业务拒绝未误报为成功 |
| 人工客服会话 | `POST /user/customerService/session/open`；他人 `GET /message/list?sessionId=3` | 新会话 3；他人 code 0 无权限 | 原会话 1/2 保留 |
| 客服发送/回复 | `/ws/customer-service/user|admin?token=<内存测试token>`，`CHAT_SEND` | 用户消息 19、管理员回复 20，双方收到广播 | 原库真实写入两条消息 |
| 客服跨用户发送 | 用户 18 对 sessionId 3 发送 CHAT_SEND | ERROR 无权限，没有新增消息 | WebSocket 归属隔离通过 |
| 客服关闭/关闭后发送 | `POST /admin/customerService/session/close`；用户再次 CHAT_SEND | 关闭 code 1，后续 ERROR 会话不存在或已关闭 | 关闭的是本次新增会话，不关闭已有会话 |
| 真实 DeepSeek | 用户 17 打开 AI 会话，`POST /user/aiAssistant/message/send`，推荐一道在售菜品、预算 50、不要辣 | 返回扬州炒饭 dishId 26、18 元、suggest_dish、analysisFallback=false | 按原配置真实联网调用，无模型替身；不保证持续额度/未来网络可用 |

浏览器及客服命令：Python 在内存生成 token，通过 stdin 调用 `node docs/verification-evidence/2026-10-05/browser-customer.cjs`，脚本源码保留（复用须核对环境及授权），调用方式使用 `probe.token()` 和本次 sessionId 3，避免命令行/文件存 token。首次 exit 0，browser-customer-results.json（历史附件已清理） 记录 WebSocket 全部消息；后续 `browserOnly=true` exit 0，browser-routes-results.json（历史附件已清理） 记录真实路由。

Edge headless 渲染登录页（2 个输入框）、`/dashboard`、`/order`、`/inform`、`/dish`；浏览器 pageerror 与 HTTP ≥400 响应均为 0。已查看 admin-inform.png（历史附件已清理），人工客服显示已连接。截图当时保存在同目录，已于 2026-10-06 清理。首次误猜 `/customer-service`、`/analysis`、`/order-risk`，被重定向 404；核对 router 后确认客服入口为 `/inform`，补测该页通过。这些猜测路径的 404 不当成业务缺陷。热度分析和风控训练的独立管理 UI 未验证，不把 API 可用扩大为管理页面完整可用。

## 故障注入、历史图片

命令：`python docs/verification-evidence/2026-10-05/run_harness.py`。javac 编译独立 `VerificationHarness.java`（本机保留，未纳入本次提交），classpath 来自本次 Surefire 报告；启动原 Spring 服务、随机 HTTP 端口、原 MySQL。最终编译/运行 exit 0，结果 harness-results.json（历史附件已清理）、harness-run.log（历史附件已清理）。初版测试材料曾使用错误 VO getter 和不匹配的 Mockito 订单查找，已修正验证材料后重跑；这些不是业务缺陷。

依赖替换仅发生在独立验证进程，结束前恢复，未修改生产代码或停止 MySQL/Redis。每个下单案例在原库真实事务中插入本次购物车、调用原代理服务，再 rollback；两次故障案例前后订单数都为 954。事务回滚不回退 AUTO_INCREMENT，也不回滚 Redis，不能以订单数相等声称所有存储状态完全不变。

| 注入/案例 | 观察 | 判断与定位 |
| --- | --- | --- |
| 推荐统计 Redis GET 抛 RedisConnectionFailureException | submit 抛出同异常，不能返回订单；事务回滚 | **不符合智能故障隔离**。`AnalysisObservationServiceImpl.readContext` 的 GET 无兜底，`recordRecommendationConversion` 直接传播到 `OrdersApplicationServiceImpl.submit` |
| 风控快照 Mapper 失败 | 原 scoreOrder 捕获异常，下单仍返回订单；随后验证事务回滚 | 该评分失败路径隔离通过；不等于任意数据库故障均可忽略 |
| 风控结果 Mapper 读取失败 | 管理员详情抛异常；用户本人详情仍返回订单 969 | **管理端不符合查询隔离要求**。adminOrderDetail 的 findLatestByOrderIds 没有可用性兜底；没有扩大为所有订单查询失败 |
| 模型服务接口抛异常（模拟上游故障） | reply 当前较繁忙，请稍后重试；dishes 空 | 提示重试通过；注入点是原 AI 服务依赖接口，不是实际 DeepSeek HTTP 503，不宣称已覆盖额度 402、网络超时等所有传输层场景 |
| 模型返回非 JSON 内容 | 返回一道候选菜，action suggest_dish，analysisFallback=false | **不符合只提示重试要求**。`buildModelRecommendReply` 的解析失败分支继续构造候选推荐，并未如实标注这次模型输出失败 |
| 天气服务返回 null、模型给合法候选 dishId 15 | 仍返回在售清蒸鲈鱼卡片 | 天气缺失不阻断推荐通过；模型使用替身，不能据此声称天气 API 已实时可用 |
| 已有 historical.png、当前 current.png | 原图片修补方法返回 current.png，调用 updateBatchById 一次 | **违反 ADR 0002**。内存案例运行原私有方法，未真实更换原库图片 |
| 历史图片为 null、当前 current.png | 同样返回 current.png，并尝试写回 | **不符合缺失仅展示兜底语义**；没有在原库制造缺图 |

图片静态证据：小程序历史/详情 WXML 直接绑定订单明细 image；未能以开发者工具验证缺图视觉兜底。原库最初 2468 条明细、缺图 0，菜品现图与历史图不同的可修补候选 0。因此原库现有数据不能直接复现换图/缺图，需要上述不落库的内存案例，不能把当前全有图当成语义正确。

`python docs/verification-evidence/2026-10-05/final_probes.py`，exit 0；对三条不同的原历史图片 URL 执行 HEAD，均 200、image/jpeg，见 history-image-http.json（历史附件已清理）。没有下载图片或操作 OSS；抽样可达不证明所有历史 URL 可达，也不证明旧图不会被查询覆盖。

## 训练、预测与数据案例

正常热度窗口选 **2026-06-02**：已有历史订单可用，执行前该日分群和预测结果均为 0，避开覆盖已有日期的结果。未回填整库、改系统时间或生成批量订单。训练原代码会按目标日期重建派生结果；此次日期先查为空，不清空已有表或旧日期结果。

| 案例 | 命令/运行点 | 实际结果 | 判断 |
| --- | --- | --- | --- |
| 热度/分群正常历史窗口 | `POST /admin/analysis/training/run?date=2026-06-02` | 13 用户快照、3 cluster、heatModelTrained=true；silhouette 0.937121；随后 heat 46 条、clusters 13 条 | 原库真实训练与预测可运行，生成真实 XGBoost/分群结果；不代表效果合格 |
| 热度结果解释/质量 | `GET /admin/analysis/metrics/offline?date=2026-06-02` | sampleCount 319，RMSE 4.026，MAE 3.2184；rmseRatio 0.785501 > 0.3，maeRatio 0.627937 > 0.2；两 pass=false | **未达到已有质量阈值**；训练 code 1 不等于效果通过。证据 followup-api.json（历史附件已清理） |
| 输入异常 | `POST /admin/analysis/training/run?date=invalid` | HTTP 200、code 0，包含 MethodArgumentTypeMismatchException 及转换信息 | 被拒绝，但提示暴露技术异常；这是参数异常，不能替代脏训练数据案例 |
| 无用户快照 | 原 runWeeklyTraining(2026-10-04)，不生成快照 | executed=false、NO_USER_SNAPSHOTS、0 用户/cluster、heatModelTrained=false | 缺失数据的服务状态明确，通过跳过处理，不记作成功训练 |
| 无菜品快照预测 | `POST /admin/analysis/prediction/run?date=2026-10-05` | API code 1、预测任务完成，日志 skipped；heat 查询 []，数据库该窗口 0 条 | **完成提示与实际执行不一致**，需要明确无数据/未执行状态 |
| 原风控模型与元数据 | `GET /admin/order-risk/models`、`/replay` | 4 版本，本地 .ser 文件存在；ACTIVE order-risk-20260528163732 | 元数据里的 PR-AUC 0.866667 等是历史训练记录，不是本轮新算出来的准确率 |
| 本轮风控重新训练 | `GET /training/readiness`；原 runScheduledTraining；`POST /training/run` | 当前训练窗口 trainSamples=0、holdoutSamples=1、正例 0，ready=false、insufficient_dataset；实际跳过，模型表仍 4 行；API 仍 code 1/triggered | 缺失数据会跳过，但 API 没有明确表示未训练；**本轮未验证成功的新风控训练**，未改时钟或批量造数据补足窗口 |
| 不存在的模型 | `GET /admin/order-risk/replay?modelVersion=verification-not-found` | code 1、{} | 空对象可返回，没有明确不可用说明 |

额外命令：`python docs/verification-evidence/2026-10-05/run_harness.py RiskCases`。最终编译/运行 exit 0；`RiskCases.java`（本机保留，未纳入本次提交）、risk-cases-results.json（历史附件已清理）、risk-cases-run.log（历史附件已清理）。用原库订单和真实 ACTIVE 序列化模型重新评分，所有风险快照/结果写入均在事务中回滚，不覆盖保存原结果。

| 风控案例 | 结果 | 限制与解释 |
| --- | --- | --- |
| 普通候选：订单 7，仅 base_score 规则命中 | 模型 29/HIGH，有 LR/RF 概率及 topFeatures | 不是已确认正常用户标签；该候选无本轮人工标注 |
| 异常候选：订单 316，high_freq_1h + suspicious_remark | 模型 5/LOW；历史规则结果曾 70/MEDIUM | 异常特征案例与普通候选排序相反，需解释融合、特征/阈值与历史版本；不能因此断言刷单确证、封禁或整体准确率 |
| 订单不存在：-1 | 不写评分，无结果 {} | 缺失订单处理可运行 |
| 缺金额/备注 | 只修改订单 7 的内存对象为 null，原库字段不修改；模型仍 2/LOW | 当前默认特征允许评分，但没有向调用方展示数据缺失；业务可解释性不足 |

原库数据质量只读 SQL 见 `final_probes.py` 和 data-quality.txt（历史附件已清理）：现存订单无负金额/缺金额/缺用户，明细无非正数量/负价/缺图；新增历史窗口 46 条预测，无负预测，范围 2.3603–8.0127。没有因缺少脏数据而往原库插入破坏性样本。**负金额、非正数量、NaN/Infinity 特征、坏模型文件、真实模型新训练成功及训练污染传播没有完整验证**。现有 Mockito 测试覆盖规则/模型分支，但不能代替这些原库数据案例。

## 原库与本地文件影响

开始只读连接成功，27 张表；原订单 953，status 1/2/3/4/5/6 分别 2/7/1/1/858/84；46 菜品、1 套餐、13 用户、2 员工。查询来源为 `probe.py sql`（只允许 SELECT/SHOW，通过环境传递数据库密码）。

结束订单 **954**，完成订单 859，其他状态数量不变；明细 2468→2469。会话 2→3，本次客服会话 3 已关闭；客服消息 18→20，AI 消息 86→88。正常 AI 会话/消息保留为演示证据。菜品 46、套餐 1、风控模型 4 未改变。

历史窗口训练增加用户快照 133→146、菜品快照 506→552、分群结果新增 13、热度预测新增 46；结束分群表 110、预测表 414。训练按现有代码写模型文件/相关 Redis 缓存，未手工迁移或清空。见 db-before.txt（历史附件已清理）、db-after.txt（历史附件已清理）、final-counts.txt（历史附件已清理）、final-tables.txt（历史附件已清理）。没有数据库全量备份或逐行前后比对，不能把聚合数一致当成所有字段毫无变化的证明。

下单故障注入曾获得自增 ID 但回滚，存在自增间隙，不人为回调主键序列。AI 注入的 MySQL 会话/消息写入也回滚，**推荐展示计数和用户 17 的推荐上下文可能保留在 Redis**；SQL 事务不覆盖 Redis，未 FLUSH 或擅自清理原缓存。应把该跨存储副作用纳入后续隔离方案，不能声称故障注入完全没有缓存影响。

构建产生后端 target、管理端 dist/类型构建缓存；验证材料与截图新增在 docs。没有改变业务源文件或 lockfile。开始已有 AGENTS.md 修改、PLANS.md 删除、后端答辩文档删除及未跟踪 GLOSSARY.md/docs，均保留。验证后仅停止本轮启动的后端/Vite及临时验证进程，原 MySQL/Redis保留运行；开发者工具服务端口保持原关闭状态。

结束用 `git diff --name-only` 核对仍仅有原三项 tracked 变化，用 `Get-NetTCPConnection` 核对 3306/6379 继续监听、8080/5173 已停止。成功运行的后端/Vite进程是人工停止，退出 -1 不算运行验证失败。初版 harness 的异常退出/残留线程已停止，最终两个 harness 均 exit 0；未把初版的 Mockito/Spring 代理替身设置错误记成产品缺陷。审计 `python docs/verification-evidence/2026-10-05/audit_evidence.py` exit 0，检查配置中的六个实际凭据值及 JWT，证据文本剩余精确凭据命中 0，见 credential-audit.json（历史附件已清理）。这不是全环境秘密扫描的承诺。

## 服务端口开启后的三端补验

2026-10-05 下午（本地时间），用户手动开启 IDE 服务端口 **57377**，并明确允许开始验证。本次直接使用原库和现有小程序产物，不清表、不迁移 schema、不修改业务代码。以下结果补充首次 API 验证，不能反推首次就已验证模拟器。

### 命令、环境与证据

根目录执行：

```powershell
& 'E:\微信web开发者工具\cli.bat' --port 57377 auto --project 'E:\Learning Files\yuweiju-takeout\yuweiju-weixin-miniapp' --auto-port 9420
npm.cmd install --prefix "$env:TEMP\yuweiju-miniapp-verification-tools" miniprogram-automator@0.12.1 --no-audit --no-fund
node docs/verification-evidence/2026-10-05/miniapp-driver.cjs <command> <evidence-label> [arguments]
python docs/verification-evidence/2026-10-05/run_miniapp_admin.py <action>
```

前两条 exit 0；wechat-enabled-auto.log（历史附件已清理）、automator-install.log（历史附件已清理）。SDK 仅安装到临时目录，无项目依赖或 lockfile 改动。连接 `ws://127.0.0.1:9420`，用户既有 DevTools 保持开启。SDK 安装有 core-js 旧版警告；没有替项目升级框架。

后端使用首次已经验证的短临时目录 JVM 参数启动同一 jar；管理端执行 `npm.cmd run dev -- --host 127.0.0.1`。miniapp-backend.log（历史附件已清理）、miniapp-admin-runtime.log（历史附件已清理） 记录运行。模拟器走项目现有 `http://localhost:8080`。临时运行时包装只记录请求 URL、状态、业务 code 和 toast，不记录请求头或 token；测试结束恢复原 wx.request/showToast。

每条 driver 命令保存页面路径、数据、请求轨迹和异常，下面列出实际 command/label/参数；浏览器命令保存日志、响应摘要及 PNG。命令 exit 0 只表示驱动完成，业务结论仍分别判断。

| 案例 | 命令（省略上述 driver 前缀） | 结果与证据 | 判定/限制 |
| --- | --- | --- | --- |
| 初始会话 | `relaunch home /pages/index/index` | 旧 token 下 shop/info、购物车 401；公共分类/菜品可读，miniapp-home.json（历史附件已清理） | 登录失效真实可复现；公共页面可显示不能证明已登录 |
| 微信登录 | `login real-login`；`vm-method clear-expired-login setToken '[""]'`；`vm-method login-ui getData`；`confirm login-confirm`；随后 `relaunch authenticated-home /pages/index/index` | wx.login 成功，后端 login code 1；项目原登录流程保存 token，店铺 status 1、购物车 code 1，miniapp-real-login.json（历史附件已清理）、miniapp-authenticated-home.json（历史附件已清理） | 用户 **24** 为原库已有用户；只读核查 openid 前缀 oO1h，非 mock_openid_dev。没有给用户端注入测试 JWT。dev 登录实现仍允许失败时 mock 回退，不扩大为生产认证验收 |
| 菜品、口味、购物车 | `tap flavor-dialog .check_but`；`tap-index flavor-1 '.more_norm_pop .item' 0`；`tap-index flavor-2 '.more_norm_pop .item' 4`；`tap cart-add '.more_norm_pop .dish_card_add'` | 凉拌三丝，实际明细口味“微辣,加葱”，POST add 和 GET list code 1，miniapp-cart-add.json（历史附件已清理） | 走现有元素点击，不改商品；初始该用户购物车为空 |
| 地址与下单金额 | `tap checkout .order_but`；`tap submit .order_but_rit` | 原地址 **31**；下单页 **25 元**，提交后订单 **974** 返回 **19 元**，miniapp-checkout.json（历史附件已清理）、miniapp-submit.json（历史附件已清理） | **金额不一致**：小程序 vendor.js:22125 加固定配送费 6 + 数量；后端 OrdersApplicationServiceImpl:223 只计商品 18 + 打包 1。尚未决定配送费最终规则，不能直接认定该加或该减哪一端 |
| 模拟支付 | `tap mock-payment .add_btn` | PUT payment code 1，进入 success；原库 pay_status 1、status 2，miniapp-mock-payment.json（历史附件已清理） | 现有 mock 支付；没有真实微信支付 |
| 管理端接单、配送、完成 | `python .../run_miniapp_admin.py confirm`、`delivery`、`complete`、`complete-observe`；用户 `navigate order-delivering /pages/details/index?orderId=974`、`navigate order-completed /pages/details/index?orderId=974` | 浏览器实际点击查看/接单/派送/完成；用户分别读到 status 4、5；原库最终 status 5，miniapp-admin-confirm.json（历史附件已清理）、miniapp-admin-delivery.json（历史附件已清理）、miniapp-admin-complete-observe.json（历史附件已清理）、miniapp-order-completed.json（历史附件已清理） | 三端代表性履约通过；管理员用原管理员 1 的短期测试 token，未验密码登录。complete 首次动作已成功，但脚本等待不存在的确认弹窗后 exit 1；后续观察命令 exit 0，不伪称首次脚本通过 |
| 人工客服双向消息 | `navigate customer-received /pages/customer-service/index`，`input customer-input .cs-textarea '小程序链路验证：请确认本次订单974。'`；`tap customer-send .cs-send`；管理端 `python .../run_miniapp_admin.py customer`；`navigate customer-reopen /pages/customer-service/index` | 用户消息 **21**、管理员回复 **22** 写入原会话 **2**，小程序 messages 可读双向内容、wsConnected true；miniapp-customer-send.json（历史附件已清理）、miniapp-customer-reopen.json（历史附件已清理）、miniapp-admin-customer.png（历史附件已清理） | 最终管理脚本 exit 0，截图已查看；已有历史消息保留，会话 2 不关闭。首次脚本重复文本定位/缺 userInfo cookie 导致发送条件不满足，修的是验证脚本，非业务代码 |
| 原配置真实 DeepSeek | `navigate ai-open /pages/ai-assistant/index`；`input ai-input .ai-input '推荐一道不辣的在售菜品，预算50元。'`；`tap ai-send .ai-send`；`inspect ai-reply` | 原配置联网成功，小程序收到红烧鱼块 dishId 38、38 元候选卡片，sending false，miniapp-ai-reply.json（历史附件已清理） | 确认客户端接收成功，不证明模型建议每次满足全部偏好或持续有额度 |
| 上游 HTTP 503 | fixture 模式 http503；`navigate ai-fault-open /pages/ai-assistant/index`；`input ai-fault-input .ai-input '推荐一道不辣的在售菜品。'`；`tap ai-http503 .ai-send` | 本地上游实际收到 /v1/chat/completions，返回 503；小程序回复“当前较繁忙，请稍后重试。”、dishes []，miniapp-ai-http503.json（历史附件已清理） | 这一传输失败提示通过；业务 API 仍 code 1 不等于模型成功 |
| 上游 HTTP 200/无效内容 | 模式 invalid；`input ai-invalid-input .ai-input '推荐一道菜品'`；`tap ai-invalid .ai-send` | 上游 content 为 invalid-model-output，小程序却显示“为您推荐一道菜：清蒸鲈鱼。”和 dishId 15 卡片，miniapp-ai-invalid.json（历史附件已清理） | **失败提示语义不通过**，与首次服务方法注入结果一致，现已确认会呈现给用户 |
| 历史订单与图片 | `relaunch history-final /pages/historyOrder/historyOrder`；`images history-images` | 历史 API code 1，含订单 974；页面绑定的数据有历史明细 image。微信 getImageInfo 对凉拌三丝、蛋炒饭、干锅花菜三张图成功，分别 720×505、2000×1122、1335×749，miniapp-history-final.json（历史附件已清理）、miniapp-history-images.json（历史附件已清理） | 页面数据读取和客户端图片解码通过；未替换/删除原库旧图，不把抽样成功当作 ADR 0002 已满足；缺图布局视觉仍未验 |

上游替身仅运行于本机 `127.0.0.1:18099`，`model_http_fixture.py`（本机保留，未纳入本次提交） 不转发请求、不记录请求头。命令 `python .../model_http_fixture.py` 启动后，停止本次启动的后端，用同一 jar 加进程参数 `--spring.ai.openai.base-url=http://127.0.0.1:18099 --spring.ai.openai.api-key=verification-dummy --logging.level.com.codeying=warn` 重启；仍加短临时目录 JVM 参数。依次 `Set-Content .../model-fixture-mode.txt http503 -Encoding ascii` 和 `... invalid ...`，实际响应证据见 model-fixture-requests.log（历史附件已清理）、miniapp-backend-fixture.log（历史附件已清理）。没有编辑 application 配置。最后停止替身和测试后端，按原启动命令恢复，miniapp-backend-restored.log（历史附件已清理） 显示 Started App；`relaunch restored-history /pages/historyOrder/historyOrder` 返回 code 1，miniapp-restored-history.json（历史附件已清理）。本次未重跑训练或 Redis/Mapper 故障注入，其结果沿用首次证据。

自动化限制单独记录：SDK screenshot 调用 5 秒内未返回，初始 JSON 保存 screenshotError；没有小程序新截图。SDK 导航 callback 等待、authorizeAllow 无授权弹窗时报错、错误原生 Vue 方法名等属于驱动调用问题；核查项目实际 Vue 方法和路由回调后再验证，历史页最终 reLaunch:ok。没有用这些脚本错误代替产品缺陷。新命令设置总 25 秒超时，避免无限等待。管理端最终截图、页面数据、真实请求和 SQL 可相互核对，但不能替代小程序逐像素检查。

### 本次增量与复评

只读核查 miniapp-db-before.txt（历史附件已清理） 与 miniapp-db-after.txt（历史附件已清理）：订单 **954→955**，明细 **2469→2470**，用户 **13→13**，客服会话 **3→3**，客服消息 **20→22**，AI 消息 **88→94**（成功/503/无效内容三个用户与助手往返）。新增订单 974 已完成、实记 19 元；下单按现有逻辑清理本次购物车，无手工清表。原会话 2 保持开启，既有会话 3 仍沿用首次状态。AI 故障结果保留为演示记录，无删除或回调主键。

结束恢复原模型服务配置，保留本次启动的原配置后端 8080、管理端 5173 供用户继续查看；原 MySQL/Redis、用户 DevTools 及已开启的服务端口保持运行，miniapp-services-final.txt（历史附件已清理）。`cleanup cleanup` 恢复临时请求/toast 包装并断开 SDK；保留用户正常登录和客服缓存。此状态不同于首次结束时停止服务的记录。Git tracked 变化仍仅为用户原有三项；补充文件都在 docs 验证材料中。再次执行凭据审计 exit 0，六个配置凭据及 JWT 的剩余精确命中为 0；报告链接核查见 report-check.json（历史附件已清理）。

三条路线的新证据：**继续维护**足以恢复代表性演示，但金额和失败提示等已有可见缺陷；**保留技术栈局部重构**能保留已跑通的页面、API、原库及客服链路，围绕具体问题划定范围，仍最受证据支持；**换栈重写**没有新增收益证据，仍不推荐。下一步应确定局部修复与小程序源工程恢复的可评审范围；无需为了决定路线再反复验证同一条成功链路，未覆盖项按具体改动风险补验。

## 未验证项与后续验证顺序

1. 现有小程序产物在模拟器中的代表性三端链路已补验；未执行重新编译完整日志验收、所有页面/状态分支与像素级 UI 验收。自动化截图接口超时，不能把页面数据证据称为完整视觉验收。
2. 原小程序可编辑源工程恢复/重建：本轮禁止改业务代码，未创建源工程、未更换界面。当前仅验证编译产物文件结构。
3. 已补验模拟器 wx.login → 后端返回已有非 mock 用户 → 小程序存储登录状态；未验证生产 profile、完整微信身份异常矩阵。管理员密码登录、所有订单状态分支、退款、客服抢占锁多管理员竞争未完整覆盖。真实支付、手机真机、公网发布和语音按已确认范围排除。
4. 已经通过本地上游 HTTP 替身补验 503 与 200/无效内容的小程序显示；真实 DeepSeek 余额耗尽 402、网络超时和重试调用控制仍未验证。替身 503 不等于真实 DeepSeek 服务曾返回 503。
5. 历史图片换图/缺图的实际 UI 和原库持久化效果：本轮只做内存方法案例，避免损坏旧图。三个 URL 可达不能代表所有图片。
6. 成功的新风控训练、更多标注案例的效果比较、热度脏特征处理，以及 Redis 已写后又异常的完整跨存储一致性没有覆盖。
7. 维护者亲自定位 SQL、解释事务与修改小练习尚未完成；本轮调用路径定位由验证者执行，不当成维护者能力验收。

小程序代表性运行证据已补齐，下一步可以确定局部改动范围，无需继续把全面重写作为默认候选。在后续获授权的改动中优先统一订单金额、解决模型坏输出、历史图片、智能故障隔离和空数据提示，再验证相同失败场景。热度效果与风险候选排序作为解释与数据案例问题分别处理，不把调整目录当成模型效果修复。

## 三条维护路线重新比较

以下依据本轮运行结果，不新增未经测量的工期、评分或版本升级承诺。

| 路线 | 本轮支持与反证 | 成本/限制 | 当前判断 |
| --- | --- | --- | --- |
| 继续维护 | 后端/管理端构建、原库下单履约、客服与真实 DeepSeek 可运行，已有资产值得保留；启动临时目录和 lint 配置属于可定位问题 | 只继续补功能会延续读订单写旧图、推荐失败返回菜、智能与订单耦合、空数据误提示；小程序缺源工程问题仍在 | 可用于短期恢复演示，但不足以满足已确认语义和解释性目标 |
| 保留技术栈的局部重构 | 失败都定位到具体服务方法、依赖调用和状态语义；常规分层、原 MySQL、现 Vue/Java 能支撑真实链路 | 需整理图片读写边界、推荐输出处理、统计/风险故障边界、训练状态与结果说明；小程序按页恢复可编辑实现并保留外观，补三端回归 | **本轮最支持的候选**。保留常规分层，围绕已复现案例划定局部范围；不把搬目录或增空接口当成解决问题 |
| 换技术栈全面重写 | 本轮没有证据证明 Java/Vue/MySQL 无法继续使用；换框架也不会自动修复模型效果、历史语义或原库兼容 | 重新发现五项业务、保留界面/库/认证与客服模型、学习新栈及重做三端验证；当前缺少同场景新栈收益证据 | 暂不推荐。若后续证明关键依赖无兼容修复路径，或局部恢复仍无法满足要求，再用同一场景比较收益 |

路线推荐与“所有验证通过”分开：小程序补验进一步支持保留既有三端资产，已有足够证据收窄到局部重构。原小程序可编辑源工程、真实风控重训和部分异常数据验证仍有缺口。不会把测试 19+2 通过、API code 1 或历史模型指标写成全面验收通过。本报告不启动任何重构、换栈、schema 改动或正式发布。
