# Issue #10 执行报告（2026-10-10）

最终候选 d0f18743e4b394aa9304cba20d2528d76a7684ca 的独立自动检查与维护者人工复验全部通过。按已授权 yuweiju-deliver 流程交付；下文保留历史失败及其修复过程，远端结果另见交付报告。

## 需求、授权及范围

[Issue #10](https://github.com/Endercloud001/yuweiju-takeout/issues/10) 实时读取为 OPEN、comments=[]；原生 blocked_by 返回 #2 CLOSED、#5 OPEN。维护者发现结算44/订单38并明确授权继续修复，单独最多两轮、编码累计60分钟、独立验证20分钟。#5 可编辑源码候选已存在但待人工验收，本票按新授权基于它继续，不假称已合并或关闭依赖。全部人工验收通过后才按会话授权交付，隔离服务在验证通过后可自动启动/保留。

起点 `e6c8af5f413fc21fd83b3d0a2100b7325df6ee81`，包含 main `612c6376a7a5e86c062424300bc722e581d7f590` 与 #5 源码恢复。新独立 runtime `.scratch/issue10-afk-20261010/runtime`，任务分支 `codex/afk-issue-10-20261010`，工作树为其中 `.sandcastle/worktrees/codex-afk-issue-10-20261010`。没有更改根分支或覆盖既有用户文件；后续交付要包含 #5、#10 两个候选及报告。

复现见 #5 报告及本票 `input/issue5-order-total-repro.json`：隔离订单4商品36、打包2、保存38，结算加配送6显示44。真实后端重算遗漏配送；此前 SDK submit 合成响应直接回显客户端amount，不能证明全链路金额一致。人工44/38截图保留在本票 `before`，未改原订单。

新规则：可信当前商品/套餐单价×数量＋每份1元打包＋固定配送2元，单份18=21、两份18=40。BigDecimal 单价两位 HALF_UP、金额不超过实际 decimal(10,2) 上限99999999.99，数量正整数、总份数不得溢出int。客户端amount/packAmount缺失/旧值/篡改均忽略；类型错误仍按原协议失败。零商品目录价至少收取包装配送，不将缺失价格当零。

购物车读取返回当前目录价格的副本，不改存储cart快照；下单再读可信目录并保存订单明细单价。结算进入和提交前刷新，失败不允许用缓存下单；价格变化提示再次确认。提交响应为最终权威。现有协议没有锁价承诺，并发再变价仍以提交响应为准，不新增锁价机制。

支付、详情、历史及管理端显示存储订单总额；历史订单不重算、不回填费用或图片。详情去掉硬编码配送6，文案为“以订单合计为准”，没有推定旧单配送2或6。管理端已有 stored amount 展示，无源码修改。认证、用户归属、模拟支付、公开字段/路径/code/schema/ID保持；不新增真实支付退款。方案为候选中 `yuweiju-document/execplans/Issue10-trusted-order-charging.md`。

## 变更文件及调用链

23 个文件，1059行新增、167行删除（仅本票相对e6c8af5）：

- 探针 `.sandcastle/environment/Issue10Probe.java`、`issue10-check.sh`；验证文档 `docs/issue10-billing-verification.md` 与上述方案。
- 后端 DTO `OrdersSubmitDTO.java` 注释；Mapper `OrdersMapper.java`、`OrderDetailMapper.java`；Service接口 `OrderDetailService.java`、`ShoppingCartService.java`。
- 后端实现 `OrderChargingPolicy.java`、`OrdersApplicationServiceImpl.java`、`OrderDetailServiceImpl.java`、`ShoppingCartServiceImpl.java`。
- 后端测试 `TrustedOrderChargingTest.java`、`CartAddressRulesTest.java`、`OrdersApplicationRiskIntegrationTest.java`。
- 结算源 `Checkout.vue`、`checkout.js`、`build.test.cjs`、`checkout.test.cjs`；生成 `pages/order/index.js`、`index.wxml`；必要的旧详情 `pages/details/index.wxml`。

用户Controller取得身份 → OrdersApplicationService事务 → 可信目录计费 → 订单/明细持久化/仅清本人cart；条件访问进入具名Mapper。正常主键CRUD保留。购物车读取复用同一计费规则；金额和明细读取保留存储语义，删除此前读订单明细时刷新历史图片的写入。Checkout复用旧Vue/uni/API运行时，并从 #5 的源码重编译，不改vendor、不扩大其他页面源工程恢复。API、数据库及管理端业务源码无变化。

## 编码证据与预算

模型 `gpt-6.1-sol` medium，Git-safe镜像 `sandcastle:yuweiju-dev-git-safe`（已检查存在）；专用认证设置保持，未复制凭据。项目内启动前工具/Git防护/网络/登录/mysql/redis预检 passed=true、exit0、预检容器 stopped=true。

监督器实际开始12:13:36.007Z、结束12:46:18.736Z，累计32分42.729秒，只使用1轮（上限2轮/60分钟）。监督器exit0，完成标记与任务提交同时存在，资源 stopped=true。无自动续跑、第三轮或预算重置。候选工作树只剩本票未提交进度证据，未暂存认证、缓存、日志和临时产物。

证据根 `.scratch/issue10-afk-20261010`：输入/配置/prepare-record、`supervisor.log`、`coding/*result.json`、`*resources.json`、逐轮日志、候选 `.scratch/issue10/candidate.json`。GitHub读取遭TLS超时后仅单次会话保留原GODEBUG并关闭HTTP2重试；最终正文、评论、原生依赖均成功，失败/成功退出和时间保留在fetch-records/retries，永久网络与凭据设置未改。

保留失败：修复前回归exit1（expected40/actual38）；只读schema检查先因JDBC依赖未就绪/版本路径错误失败，安装后实际路径重试成功，无数据库写入；package批次1有一个Mockito重复设桩错误（74测试无断言失败），用doReturn修正后完整package批次2通过。没有缩小测试集或把这些错误算成功。

最终代理检查（未冒称宿主独立或人工）：

| 命令，快照根为cwd，cd项另指目录 | 结果 |
| --- | --- |
| `mvn -B -f yuweiju-backend/pom.xml package` | exit0，74/74、无skip，Boot package完成 |
| `bash .sandcastle/environment/issue10-check.sh` | exit0，businessPassed=true、cleanupPassed=true |
| checkout-source `npm ci && npm run build && npm test` | exit0，14/14、无skip |
| admin `npm ci && npm run lint && npm run typecheck && npm run test && npm run build` | exit0，四项检查，3测试 |
| 指定方案/验证报告文档链接检查，diff/scope检查 | exit0 |

代理探针实际使用Spring事务代理、MyBatis/MySQL、Redis及HTTP，独立于Mockito；覆盖21/40/97、客户端/缓存篡改、当前目录改价、精度/数量/金额边界、空车/缺商品/归属/认证、orders/order_detail自有触发器失败回滚和重试、历史合成19及图片、用户/admin/历史金额一致。finally仅清自有行/键/触发器/文件，并比较原夹具未变；SQL回滚不代表自增/Redis/文件回滚。这里只证明合成历史19不重算，未连接原库检查真实974。

## 精确独立验证与资源

独立开始时间见 `verification-start.json`，上限20分钟。独立快照 `independent/task-review-1791636409531934253/snapshot`。配置 `independent-config.json` 执行与代理同样五组真实命令，独立全新MySQL卷/Redis及npm/Maven环境，不挂认证、不调用模型。结果与结束时间待下文追加。

编码mysql/redis已按仅本票Compose项目down，具名出口网络已移除，保留合成卷及证据；编码容器 stopped=true。独立mysql/redis当前为验证自有资源；原 #5 验收服务保留，未在独立结果前升级。

## 人工复验与交付边界

独立通过后，将保留的 #5 合成验收库及用户订单用于新版候选，保持当前 baseline-miniapp 的维护者AppID/信任配置；使用精确候选构建，已有库不会清空或追改订单。停止入口只针对同一具名验收Compose并保留合成卷。官方真实隔离HTTP/截图及启动检查结果待追加，不以合成submit回显代替计费检查。

人工复验：单份18新单21、两份18新单40；结算→支付→详情金额一致，老38订单仍38；地址/备注/餐具/配送时间与模拟支付及购物车联动继续正常。人工全部通过前不交付 #5/#10，不清卷或自动停掉待反馈服务。

## 独立自动检查与服务实际完成记录

独立开始12:46:48.172Z，resources.json写完13:00:21.005Z，13分32.833秒；全部5组exit0，容器exit0/stopped=true、authMounted=false、modelCalled=false。Maven74测试、checkout14测试均无失败/跳过，真实Spring/MySQL/Redis/HTTP探针businessPassed和cleanupPassed均true，admin lint/typecheck/test/build通过、3测试；两份18的新订单提交、用户详情、管理员详情和SQL均40，历史合成19保持。构建大chunk警告保留，未为了本票扩大到前端拆包。

实际逐项命令和cwd与config一致；`check-0..4.log`、`exit-status.tsv`、runner/environment/resources均在独立目录。代码scope/diff普通检查exit0，未新增hash/冻结合同/gate。编码和独立Compose项目容器查询均为空，出口网络已移除，证据 `stopped-projects.json`；合成卷和失败/成功日志保留。只有既有验收项目五个容器刻意运行，不操作其他任务。

13:00后按授权升级 `yuweiju-issue5-live-20261010` 的 backend/admin，使用本票精确独立快照target/classes和`.scratch/issue10/libs`，不再借用历史业务构建；probe以外的本机地图启动器仍固定7分钟。mysql/redis及现有合成库保留，localhost18085/18086保持。`live/previous-environment`备份旧验收配置；`live/prepared.json`记录来源与资源，`live/stop.ps1`只停止该项目并保留卷，不声称代码回退能撤销已产生数据。

baseline-miniapp 的tracked源/产物从精确快照复制，维护者project/private配置未覆盖，只在副本vendor改localhost服务地址。没有修改正式vendor，未改变信任/安全设置。服务ready检查13:01:38.700Z exit0：HTML/Vite资源、店铺code1、匿名拒绝和模拟用户认证读取均通过；只创建合成登录用户。`check-retained.py` exit0：升级前已有订单id4的amount/pack_amount完全相同，38仍38；证据`live/retained-orders.json`，只读，没有访问原库。

已请求维护者在同一受信任项目点击“编译”。真实SDK复验驱动已完成Node语法检查，但未收到重编译确认前不执行；不得把准备好的`real-sdk.cjs`写成已运行。它将使用专用模拟用户的真实后端，检查checkout40→submit40→detail40、明细36/packing2和截图，并恢复当前用户上下文；写入仅本票合成地址/cart/order，不是原库测试。官方结果待后续实际记录，自动检查通过不代表维护者全部验收。

## /retro：一次宿主复盘

先核对监督器/独立资源stopped及两个Compose空列表，再读取代理result、失败/最终日志、候选diff和独立检查，实施本次一次全Loop复盘。当前没有可调用的`/retro`命令工具，按技能在宿主完成同等步骤，未虚构工具调用。以下为环境改进建议，与已交付业务区分，未改通用运行时/技能：

| 证据 | 影响与建议 | 实施状态 |
| --- | --- | --- |
| #5合成submit直接回显amount掩盖真实38 | 金额场景必须用真实隔离后端比较报价、响应、明细/SQL与两个客户端；普通业务测试足够 | 本票真实代理及独立探针已覆盖，官方实际显示待重编译 |
| agent最初花时间定位task-input | 任务prompt首行写实际挂载绝对路径，减少无谓全盘搜索 | 本票找到既有挂载后继续，通用prompt未改 |
| 初始JDBC缺失/错误版本路径 | 安装完成后按真实依赖路径执行只读schema核查，再开始写入 | 本票纠正并通过，无schema迁移 |
| 重复Mockito设桩触发旧throw行为 | 对这种测试夹具用doReturn；保留失败，重新跑完整集合 | 本票74测试通过，未缩小测试范围 |
| 官方截图曾超时、重编译依赖人工 | 自动检查和官方/人工状态分开，优先复用受信任项目并提醒编译，截图设限 | 本票保留服务/用户数据，未代答安全设置 |
| 独立admin依赖准备耗时 | 未来考虑项目内独立依赖缓存，仍执行锁文件安装和完整检查，不复用成功证据 | 仅建议，本票新鲜环境已通过，未增加gate/hash |

失败、准备错误和未完成官方项均保留。监督器结束后未重启；若再收到人工失败，先定位并遵守结束运行的恢复授权、剩余预算与轮数边界，不自动重置。


## 最新人工验收结论

维护者对精确版本 d0f1874 回复：『以上全部通过，无异常』。确认 baseline-miniapp 已重编译，结算外观、地址/备注/餐具/配送时间正常；单份18新单21元、两份40元，结算→支付→详情一致，旧38元订单仍38元；模拟支付成功、购物车联动正常。此结论替代此前待反馈状态，不抹去历史失败记录。原库974未访问或修改；隔离历史单与代码检查用于验证不重算，未宣称原库写入验收。沿用会话既有提交、推送、PR、合并、同步和关闭授权。
