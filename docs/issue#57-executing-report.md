# Issue57：打烊拒绝新单、保留旧单支付履约

范围仅主题02 N1 / #57。来源提交 `e07d5e8a52ef4b25bc6f15b8da852c4c2cf7ccc0`（#56 已合并 main，#55 已交付），任务分支 `codex/issue57-closed-shop-submit-20261018`。本报告随最终成功候选提交保存；精确 SHA 由 Git 与 `.scratch/issue57/candidate.json` 记录。截止 `2026-10-18T11:12:56.643Z`，MAX2 supervisor iterations / 60min 含 closing2min 未调整，重复检查为 check batches。

生产改动仅 `OrdersApplicationServiceImpl`：构造器注入既有 ShopStatusService，合法 submit 参数校验之后、订单/明细写入及清车之前读取状态。现有状态0拒绝并返回“店铺已打烊，暂时无法下单”；RuntimeException 包装为保留 cause 的 OrderBusinessException，返回“店铺状态读取失败，请稍后再试”。UserOrderController 原有异常分支转为既有 ApiResult（code0/success=false/message=msg/data=null）。状态不可读时明确拒单。

实际 prod `ShopStatusServiceImpl` 仍只将 Redis `yuweiju:shop:status` 值`"0"`视为打烊，缺键/空白/其他值按原规则营业；dev `ShopStatusServiceDevImpl` 固定营业、忽略关店写入。现有 @Profile("prod")/@Profile("dev") 和 bean 规则均未更改。仅承诺提交时状态读取，Redis 切换与 SQL 提交没有分布式原子性保证，已在途请求不作原子阻断承诺。旧单支付/取消/接单/配送/完成均不增加营业检查。

三端：后端承担规则和既有代理事务；管理端现有 `/admin/shop/{status}` 开关继续使用同一服务，无新增 UI；小程序结算已有失败 toast、finally 解锁、保留地址/备注/餐具/购物车，补实际编译页面按钮运行时回归，无生产页面改动。可信价格、份数打包费、配送费、原订单金额/历史明细图片、身份归属、条件状态更新、事务行为保持。不涉及 #58/#65/#66、调度、真实支付、schema/API结构/配置迁移、模型/外部服务或全局框架。

## 检查与证据

所有命令在 `/home/agent/workspace`，非登录 bash，确认 java=`/opt/java/openjdk/bin/java`、mvn=`/usr/share/maven/bin/mvn`。任务临时目录 `.scratch/issue57`；JAVA_TOOL_OPTIONS 仅将测试临时目录定向到该目录。没有原库连接或原配置导入。

初始 batch：checkout ci exit0；test exit1，14/16通过，两个新用例误用不存在的 store.state.dishList。保留 `.scratch/issue57/initial/commands.json` 与 `2.log`，读实际 vendor/store 后改为 orderListData；这是测试夹具纠正，无页面生产缺陷、无监督器重启。corrected batch 的 ci/test16/build/compile通过；package 首轮174项有1项新dev context夹具断言失败：Spring对注册Redis mock进行生命周期调用，被无交互断言捕获。保留 `initial-corrected/5.log`，删除不必要的Redis bean注册，改为断言dev context根本没有Redis bean；固定营业/唯一profile bean/无交互断言保留。后端纠正batch的compile/package174/probe112断言全部exit0，全部必需命令顺序如下，命令/目录/退出/日志均保留：

1. `npm --prefix yuweiju-weixin-miniapp/checkout-source ci --no-audit --no-fund`
2. `npm --prefix yuweiju-weixin-miniapp/checkout-source test`
3. `npm --prefix yuweiju-weixin-miniapp/checkout-source run build`
4. `mvn -B -f yuweiju-backend/pom.xml compile`
5. `mvn -B -f yuweiju-backend/pom.xml package`（含全部普通测试）
6. `bash .sandcastle/environment/issue57-check.sh`（自身等待 package exit0，再从完成的 Boot jar 提取依赖、javac、启动探针；独立 verifier 不依赖 worker 缓存/历史行）。

普通测试：TrustedOrderChargingTest 的营业成功、可信金额/费用原断言保留，新增关店/RuntimeException 拒绝、cause/提示、无订单/明细写入和不清车；三个手工构造器夹具提供营业状态 mock。OrderFulfillmentRulesTest 新增关店旧单 mock-pay、两种可取消状态和接单/配送/完成；Issue19ShopStatusTest 通过真实 dev profile 小 Spring context 选中唯一原 dev bean，关店仍返回1且不访问 Redis。checkout 回归从仓库生成 WXML 取去支付按钮事件、通过实际旧运行时 __e/Vue/store/API 分发，输入真实新响应结构并检查 toast、解锁、地址/备注/餐具/金额/购物车保留，营业重试进入原支付页。此 Node 运行时证据不冒充官方工具验证。

初检真实探针 `.scratch/issue57/sql-GbmSR1nE`：112条断言通过，businessPassed/cleanupPassed/springClosed/httpClientClosed均true，failure=null。首轮两次夹具纠正均保留日志，没有修改原有断言制造通过。Ponytail完成了起点以来所有任务diff/调用方/测试/配置的full评审，采用删除探针重复Redis开关写入和合并重复测试导入；拒绝额外生产抽象、全局异常处理/默认语义改造、生产前端改写及新fixture接口。权限/归属/费用/事务/清理证据保留。已明确 stop ponytail / normal mode，最终全部必需检查按原顺序重跑通过。

最终 check batch：`.scratch/issue57/final/commands.json` 六项命令全部exit0；checkout **16/16**，Maven **174/174**，compile/package/build均成功。最终探针 `.scratch/issue57/sql-zbZiUw08` 独立新UUID夹具，**112条断言**，businessPassed/cleanupPassed/springClosed/httpClientClosed均true、failure=null；脚本自带第二次package174/174→提取138个Boot依赖→javac→probe，依赖顺序真实执行。采用的两处精简已纳入此最终检查。汇总 `.scratch/issue57/checks-summary.json`；两次首败和所有命令/目录/退出/日志保留。最终检查后仅更新报告/进度，未修改生产/测试/探针代码。源码与证据均在授权项目内，原始业务服务、配置、数据未触及。

## 自包含隔离探针

`.sandcastle/environment/Issue57Probe.java` / `issue57-check.sh` 复用已审查55/14/10的 package、代理/HTTP、隔离配置模式，不运行旧训练/模型/原库探针。standalone application-afk.yml + CLI 强制 `mysql:3306/sandcastle_fixture`、`redis:6379/db0`，禁用 scheduled processor、analysis/risk任务/训练/评分、XGBoost；模型路径限制在本次 scratch，唯一 map seam 固定7分钟，无外部地图/微信/天气/OSS/模型调用或真实付款。

写入前确认 DATABASE()、900001 user/employee/category 合成标记、实际所有表 PK/列/索引/约束和核心表 InnoDB；按实际 PK 排序的全库行快照仅在内存比较，证据仅 schema 元数据和布尔断言。每次 UUID 派生新数字 ID、UUID 名称、参数绑定，确认各 ID 和 cleanup 所有者范围无旧行。保留其他用户购物车及全部原 SQL/Redis 内容；状态 key 原始 DUMP 与绝对过期时刻在内存备份，最后只恢复该临时占用 key 和剩余 TTL，不输出值/token/HTTP SQL body。

实际 prod ShopStatusServiceImpl（不是替换状态 bean）通过既有管理员开关和真实 Redis读取：0拒单、WRONGTYPE list 使真实服务抛异常并拒单，拒绝期间零核心写入尝试、完整旧 cart/全 SQL 快照不变；1允许实际 HTTP submit，新订单/明细可信总额40.00=18×2+2份打包费+2配送费，清空仅当前车。MyBatis observer 只观察真实写入和活动事务、不伪造 Mapper；实际 SQL确认持久化。关店 OLD 待支付订单实际 mock-pay→待接单→接单→配送→完成，另测待支付/待接单用户取消、已接单管理员合法取消，以及匿名/跨用户/用户凭据（既有不同secret校验）访问管理员入口拒绝及快照保留。旧单历史图片和明细保持不变。

清理只删除新归属用户的订单/明细/车、自己的地址/菜品/用户并恢复状态/TTL，比对原 SQL/Redis；不做全局 cleanup、宿主服务停止、表重命名或 DDL；仅关闭探针自有 Spring context 与 HttpClient。自增序列可能前进，不将行快照声称为序列回退。每次全新 sql-XXXXXXXX 目录保留失败及最终 check-result/scenario-results，不覆盖历史成功/失败。

## 宿主精确候选独立验证与官方场景（已完成）

源码候选 `9b3c2bff3592ee9bd468fc05f3b61b49361a3607`；起点为已交付 #56 的 main `e07d5e8a52ef4b25bc6f15b8da852c4c2cf7ccc0`。AFK 第1轮完成、Ponytail 已结束，最多2轮/60分钟编码与20分钟独立验证预算未扩大，没有重启模型。

统一 verifier 在精确提交的新快照执行以下3组完整命令，均 exit0、independentVerification=passed、stopped=true，无认证挂载、无模型调用：

```sh
npm --prefix yuweiju-weixin-miniapp/checkout-source ci --no-audit --no-fund && npm --prefix yuweiju-weixin-miniapp/checkout-source test && npm --prefix yuweiju-weixin-miniapp/checkout-source run build
mvn -B -f yuweiju-backend/pom.xml compile && mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue57-check.sh
```

独立结果：结算页面16项、后端174项测试、真实SQL/Redis112条断言通过。证据在项目 `.scratch/issue57-afk-20261018/independent/task-review-1792320430582423116/`；规范与需求双轴只读审查各0发现。

官方工具2.02.2608080/SDK3.17.4，同一隔离项目9457，候选真实prod Spring/MySQL/Redis及自有合成身份，生产认证和归属继续校验。Windows受保护API/代理正确身份通过、匿名拒绝；数据服务只在本票internal网络，后端另接具名出口网以发布127.0.0.1端口，不连接原库。

| 官方场景 | 实际结果 |
| --- | --- |
| 结算后管理员关店，点击去支付 | 打烊toast、表单保留/可重试；无新订单明细，购物车和SQL不变 |
| 实际prod状态key WRONGTYPE，点击去支付 | 状态读取失败toast；相同表单/购物车/SQL保留 |
| 恢复营业，同页重试 | 可信40元新订单及明细、仅自己车清空，进入支付页 |
| 关店OLD订单从详情立即支付→确认支付 | 真实WXML按钮；仅一次受保护payment PUT/code1，SQL待接单/已支付；原管理员接口3→4→5，购物车不变 |

前三项保留原 `official/business-results.json` 逐项通过证据；其原overall=false表示当时第四项SDK超时未完成，不覆盖失败记录。第四项恢复批次 `sdk-recovery1/official/old-pay-result.json` passed=true/exit0；综合 `final-verification-summary.json` 绑定同一候选并列四项通过。

SDK恢复前只读连接发现已在pay页，完整data16ms成功；新夹具使用新UUID和正确+08墙时间、处于15分钟支付窗口。本次重建连接及Page对象并逐步骤有界记录，详情data33ms成功，真实支付只点击一次。不存在源码改动或AFK重跑。旧超时未稳定复现，不将可能的旧Page/跳转回执失效当作已确定的SDK底层原因。

一次截图saveFile限额失败仍保留，未清理其他任务存储，恢复批次没有在支付前重试该截图。复用同一项目前端未变的官方就绪图像；业务结果由真实按钮、路由、受保护HTTP和SQL证明，不声称新增支付成功截图或真实微信双账号/真实付款。用户明确免除逐票人工验收，以上均由Agent验证。

## 最终清理、复盘与交付边界

恢复批次先清理自有UUID行、恢复原status DUMP/TTL，比较完整原SQL/无关Redis，再关闭Spring：originalSqlRowsPreserved=true、redisPreserved=true、springClosed=true，后端退出0。Windows网关按PID和命令行核实后停止；MySQL/Redis停止，卷和全部首次失败/恢复证据保留。自增序列可能前进，不声称序列回滚。无原库、外部模型、实际付款或生产配置变更。

复盘采用：Windows受保护API实际就绪探针、Fresh Page读取、支付事件只发送一次并查持久结果、清理先于Spring关闭。截图额度与业务证据分开记录。未引入共享框架、安全门槛或SDK库补丁。

交付工作树从最新main快进到候选，无冲突；后续仅补本报告，业务/测试/探针源码与上述精确提交一致，故复用其验证结果。PR/最终head/merge/issue状态以宿主交付记录和GitHub读取为准。下一票以实际已合并main开始。
