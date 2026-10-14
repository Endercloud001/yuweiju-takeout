# Issue21 三端联动执行报告

起点严格为 main `b50afbe396d048e566106e93b48fb67a957828b6`；依赖2–20已关闭。范围为已交付切片联合验收。未新增业务、算法调优、schema迁移、hash/contract/baseline/gate或恢复PLANS。候选 SHA 由宿主在验收记录填写；worker 工作树结果不冒充精确提交的独立验证。

## 三端影响与实施

后端新增一个普通联合探针及脚本，并校正既有#9探针的两处pre-#10假设及#13旧费用断言：`bash .sandcastle/environment/issue21-check.sh`，从完成的 Boot jar 提取真实类/库，在真实 Spring 事务代理/MyBatis/MySQL/Redis 上走正常密码登录→公开目录→购物车→归属默认地址→可信21元→模拟支付→管理端接单/派送/完成→同一用户历史/详情及SQL。补少量报表/工作台真实查询断言；不注入管理员token冒充密码登录。用户登录使用独立WechatService合成openid，不是wx.login。

管理端完整fresh安装与lint/typecheck/test/test:refimg/build；小程序checkout-source/history-source分别fresh安装、测试与实际源码编译，四文件/每页的既有输出范围不写shared/vendor/runtime。三端业务源码无diff：尚未观察到需要修复的实际行为故障，已交付职责不因类数重构。正常/缺图/失败视觉与官方编译由宿主人工对照，不能用Node测试替代。

## 十组实际职责审查

具名Mapper的标准主键CRUD仍保留BaseMapper；复杂聚合/Join用既有XML绑定参数。每组合理无生产diff，代表回归见右列；本轮全部代表回归已通过；完整每场景断言/exit/cwd/version/resources见 `issue21-worker-results.json`，不能推导所有状态分支都已覆盖。

| 组 | 实际入口→Service→具名Mapper/IO | 检查结论与代表回归 |
| --- | --- | --- |
| 身份/员工 | AdminEmployeeController→EmployeeApplicationService→EmployeeMapper.findEnabledByCredentials/pageByName/countByUsernameExcludingId；UserAuthController→UserService→UserMapper.findByOpenid；JWT/Blacklist保留 | 密码等值与启用身份兼容；#7真实密码、旧入口退休、Redis注销；#21正常密码与错误拒绝、两独立合成用户 |
| 目录 | Admin/User Category/Dish/SetmealController→CategoryApplicationService/CategoryService、DishApplicationService、SetmealApplicationService→CategoryMapper.listEnabled/pageCatalog、DishMapper.listCatalog/pageCatalog、DishFlavorMapper.listByDish/deleteByDishes、SetmealMapper.listCatalog/countSellingInIds、SetmealDishMapper.listBySetmeal/countByDishes | 可售与关联删除规则在用例中，主键CRUD合理薄类；#8排序/空页/停售/事务；#21公开目录18元缺图 |
| 购物车/地址 | UserShoppingCart/UserAddressBookController→ShoppingCartService/AddressBookService→ShoppingCartMapper.findByUser/findItem/updateQuantityOwned/deleteOwned/deleteByUser；AddressBookMapper.findOwned/findDefaultByUser/clearDefaultExcept/setDefaultOwned/updateOwned/deleteOwned | 归属与默认规则复用成立，事务真实代理；#9跨用户、回滚、数量；#21默认地址与可信购物车 |
| 订单/支付/履约 | UserOrder/AdminOrderController→OrdersApplicationService/OrderChargingPolicy→OrdersMapper.findOwned/findByNumberOwned/findHistoryPage/payPending/transitionFrom/selectAdminConditionPage/countByStatus；OrderDetailMapper.findByOrder | #10可信收费、#11状态矩阵/竞态、#12历史读不写、#13核心回滚vs附加降级、#14风险查询降级；#21同一密码身份完成21元链 |
| 人工客服/WS | User/AdminCustomerServiceController、CustomerServiceWebSocketServer、CustomerServiceReplyLockTask→CustomerServiceApplicationService→CustomerServiceSessionMapper.findLatestOpenByUser/findByIdForUpdate/touchOpenSession/closeOpenSession；CustomerServiceMessageMapper.findHistory/findLastMessage | 身份/会话归属在Service，WS协议/广播/锁与清理在入口；#15真实双向WS、越权/关闭/竞态/事务 |
| AI/天气/模型 | User/AdminAiAssistantController→AiAssistantApplicationService、AiModelService、AiWeatherService→AiAssistantSessionMapper.findOpenToday/updateReplyState、AiAssistantMessageMapper.listTodayBefore/findLast、DishMapper.listOnSaleForReply、OrdersMapper.listRecentForRecommendation、OrderDetailMapper.listDishDetailsForOrders；模型/天气HTTP独立职责 | #16合法返回实际卡片，畸形/402/503/超时无假卡，候选停售再校验、历史/地区/热度/分群具名查询；外部全是loopback夹具 |
| 热度/分群 | AdminAnalysisController、AnalysisTask→AnalysisApplicationService、HeatModelStore→AnalysisFeatureMapper.buildUserSnapshots/buildDishSnapshots；UserBehaviorFeatureSnapshotMapper/DishTimeseriesFeatureSnapshotMapper.findDaily/deleteDaily；DishHeatPredictionResultMapper.deleteWindow/listActiveByHeat；UserClusterResultMapper.findLatestForUser/listDaily/deleteDaily；OrdersMapper.listCompletedInWindow | #17实际XGBoost训练保存重载预测和Smile分群；失败/跳过/旧模型分开，不调阈值；事务、Redis和文件副作用分别陈述 |
| 风控 | AdminOrderRiskController、OrderRiskTask、订单附加评分→OrderRiskService→OrdersMapper.findRiskWindow/findRiskBackfill/countRiskHistory/averageRiskPaidAmount；OrderRiskFeatureSnapshotMapper.findForOrders/deleteVersion；OrderRiskResultMapper.findLatest/updateDecision/deleteVersion；OrderRiskFeedbackMapper.findForOrders；OrderRiskModelMetaMapper.findActive/findVersion/archiveActive；OrderRiskStandardizerMetaMapper.deleteVersion | #18新真实LR/RF训练/文件/激活/评分/反馈事务；缺数据、跳过、部分失败、旧ACTIVE、标签来源分开报告 |
| 报表/工作台/店铺 | AdminReport/AdminWorkspace/AdminShop/UserShopController→ReportApplicationService/WorkspaceApplicationService/ShopStatusService→OrdersMapper.aggregateBusinessByOrderTimeRange/countByStatusAndOrderTimeRange/sumAmountByStatusAndOrderTimeRange/countAllOrders；UserMapper.countCreatedInRange/countCreatedThrough；OrderDetailMapper.top10；Dish/SetmealMapper.countByStatus；prod店铺Redis IO | #19既有普通测试覆盖聚合/时间/空值/导出；#21真实当前SQL营业额/完成数/TOP10/工作台与店铺。dev店铺固定开店且set无副作用，不能称prod Redis写入验证 |
| 上传/默认图 | AdminCommonController→UploadService/DefaultImageLibraryService→AliOssUtil真实SDK loopback IO（无业务DB查询，因此无需Mapper） | #20扩展/大小/路径/分页/缺省图/安全边界、流和SDK资源关闭；无真实OSS上传 |

## A1–A5映射

| 条件 | 本轮代表业务断言 | 限制 |
| --- | --- | --- |
| A1 金额一致 | #10/#21新单18+1+2=21，多数量/菜品套餐/非法金额/可信费用；checkout源码和admin消费者回归，SQL同用户/admin详情一致 | #21隔离合成974保留19；原始974历史证据/本轮官方截图待host核对 |
| A2 模型失败语义 | #16真实本地HTTP合法输出、畸形/非法候选/402/503/超时，失败无卡、天气缺失不阻断、在售再校验 | host真实模型≤4次待执行，fixture不是业务效果验收 |
| A3 历史图片 | #12用户历史/详情、admin页/详情，当前图B与历史图A/空缺图不写回；#21同一链路历史图只读；两源码及admin缺图回归 | 可加载兜底/保持布局须官方三端截图；缺失六份旧原材料不恢复 |
| A4 智能故障隔离 | #13推荐统计/附加风控故障不阻断核心，下单核心SQL故障完整回滚；#14风险附加不可用不伪LOW，风险筛选失败不退无筛选 | 隔离MySQL/Redis/HTTP/文件；SQL不撤销缓存/文件/自增 |
| A5 分析执行状态 | #17新XGBoost/Smile输出、#18新LR/RF文件/激活与指标；无数据/失败/旧active不冒充新训练，非法参数友好拒绝 | 合成样本证明执行机制，指标/阈值通过与业务效果分别记录 |

## 实际命令、证据与隔离

非login bash，已确认Java `/opt/java/openjdk/bin/java`、Maven `/usr/share/maven/bin/mvn`，真实编译POM release17运行Java21。初次在项目根误跑compile exit1 (MissingProjectException) 留 `.scratch/issue21/backend-compile.log`；同轮纠正到后端，不隐藏失败。正式检查命令见 `.sandcastle/task-progress.md`；每次复验按check batch报告，逐项exit/业务断言在 `.scratch/issue21` 和既有各probe evidence目录保留。

现有11/12/14使用实际root/config参数；classpath采用#10从完成jar提取库写到当前root，不把固定/workspace脚本的挂载差异误称缺依赖。宿主preflight `official-readiness.json`记录10:00官方Tool.getInfo/currentPage/首页截图就绪（工具2.02.2608080、SDK3.17.4），这不是本候选源码编译、wx.login或全场景验收。新增脚本允许host提供 `SANDCASTLE_CANDIDATE_COMMIT`；Git-less独立快照的sourceDirty为unknown/null，自检通过，精确SHA绑定由host verifier资源证明。

所复用脚本先审查：7–16/20只在专属sandcastle_fixture创建合成ID并清理拥有的行/键/触发器；14暂改名专属库表并finally恢复，不与其他探针并发；17保留独占新日期窗口的合成训练数据，18新独占schema结构复制；各训练独占空Redis DB与fresh scratch模型，不清理别人的run/db/模型。数值阈值/窗口/特征/模型版本/样本数/本次新输出与旧active在最终结果单独列出。

#21先查实际连接catalog/host、Redis位置和schema再写。普通snapshot使用risk两复合键，不假设每张表id。每个run独立目录保留，cleanup只合成openid/remark/name对应行与用户context键；断言原夹具完整相等、Redis原键和值不变、Spring关闭。所有HTTP替身loopback，无原.env、宿主凭据、真实WeChat/模型/OSS请求。回退仅本票提交；不会逆转自增/文件/缓存/外部影响，无数据库迁移。

## 本轮新训练产出与效果限制

| 项目 | 新产出（第一次成功训练时） | 实际样本/窗口 | 指标与原阈值，单独解释 |
| --- | --- | --- | --- |
| 热度 | `analysis-heat-xgb-2040-06-29-1791974581231`，feature `v1`；真实新JSON文件保存/重载/预测5行 | fresh合成660订单/9用户/4菜品，加原task fixture产生11用户/5菜品快照；训练347、eval33。历史2040-03-01至06-29（右开）；heatTraining dataset2040-03-31至06-22，eval06-16至22；预测06-29至07-05 | RMSE0.0029、MAE0.0020；比率0.000215/0.000146，对原0.30/0.20均pass。未来隔离窗口，不是当前营业效果 |
| 分群 | `analysis-cluster-2040-06-29-1791974570629`，新3群/11条SQL分群记录 | snapshot/clustering窗口2040-05-30至06-28，userSnapshot11 | silhouette0.6593762018323388，对原0.30 pass；后续故障注入时已成功聚类可能保留为PARTIAL，不归为热度新训练成功 |
| 风险 | 新 `order-risk-20261014184707-4f81ac59` feature `v1`，status TRAINED、fileSaved/metadataSaved/activated均true；原schema/model/file未挂载 | train181/holdout59、positive90/30；241窗口单/240完整snapshot；239显式合成反馈、2规则来源、1缺失排除。UTC train2026-06-16T10:47:07.059至09-14同刻；holdout09-14至10-14同刻 | PR-AUC1.0、RecallTop20=0.4、FPR0.0。minPositive50；LR/RF0.4/0.6、风险12/16、既有PR-AUC退步0.02/Recall退步0.05不变。无旧active，首次激活原因no_active_model，不宣称业务准确率 |

热度原在线CTR/CVR阈值0.15/0.30保留，本轮没有真实线上效果验收。效果pass与实际执行成功是两件证据；未调算法或阈值。前置#18旧模型 `order-risk-20261014160407-18237e84` 仅历史材料，未计入本轮新成功；已有ACTIVE不让新的SKIPPED获得trained=true。#17/#18无数据/不足/缺模型/失败/部分失败断言见机器结果，不能以API code1推断训练。

实际资源：#17独占task-fixture的上述日期窗口、Redis DB1、`.scratch/issue17/run-FhYEnRBy/models`；#18独占 `sandcastle_i18_1cef268b8d90427d`、Redis DB2、`.scratch/issue18/run-l4SclHnS/models`，保留SQL/模型/部分失败文件与markers，finally移除本轮触发器并关闭context。训练仅2 CPU线程上限，开关禁用后台任务，无外部HTTP；不清别人的schema/DB/model/run。原common `sandcastle_fixture`、Redis DB0的合成链探针则只清理自身ID/key。

## 检查批次与可复现命令

cwd均在当前project root下，日志/exit见 `issue21-worker-results.json` 的buildChecks/checks（逐项真实路径/参数），全部初轮适用检查均exit0；首失败另列。临时classpaths遵循完成jar的#10库提取模式，backend-classpath兼容文件不stage。

| cwd | 实际命令 | 初轮结果 |
| --- | --- | --- |
| `/home/agent/workspace/yuweiju-backend` | `mvn -B compile`；`mvn -B package` | 均0，171普通测试；真实事务另外验证 |
| `/home/agent/workspace/yuweiju-web-vue/yuweiju-admin` | `npm ci`；`npm run lint`；`npm run typecheck`；`npm run test`；`npm run test:refimg`；`npm run build` | 各0，35 Vitest +4真实Linux素材脚本用例 |
| `/home/agent/workspace/yuweiju-weixin-miniapp/checkout-source` | `npm ci`；`npm test`；`npm run build` | 各0，14 tests，结算四个输出有效 |
| `/home/agent/workspace/yuweiju-weixin-miniapp/history-source` | `npm ci`；`npm test`；`npm run build` | 各0，37 tests，历史/详情输出有效 |
| `/home/agent/workspace` | `bash .sandcastle/environment/issue21-check.sh` | 0，91业务断言、原夹具/Redis/服务清理通过；最终受影响9/13/21复验均通过 |

其余root命令分别为 `bash .sandcastle/environment/issue7-check.sh`、`issue8-check.sh`、`issue9-check.sh`、`issue10-check.sh`、`issue13-check.sh`、`issue15-check.sh`、`issue16-check.sh`、`issue17-check.sh`、`issue18-check.sh`、`issue20-check.sh`；每个命令实际完整bash/绝对path参数保存在机器结果。11/12/14实际分别执行 `bash .sandcastle/environment/issue11-check.sh /home/agent/workspace /home/agent/workspace/.sandcastle/environment`、`bash .sandcastle/environment/issue12-check.sh /home/agent/workspace /home/agent/workspace/.sandcastle/environment`、`bash .sandcastle/environment/issue14-check.sh /home/agent/workspace /home/agent/workspace/.sandcastle/environment`。17/18额外compile/package均通过，171普通测试，完成jar时间分别10:41:42/10:45:59Z，业务jar源码与startCommit相同，无旧失败build替代。

Ponytail full已读挂载技能/upstream，审查全diff、新untracked probe/report/result及调用点/配置/所有失败证据；没有额外代码简化，拒绝框架/依赖/机械重构与新版本gate。采纳报告准确性更新，保留全部安全/事务/真实训练断言。已明确 `stop ponytail / normal mode`，普通最终9/13/21受影响复验均通过；其他required checks代码完全相同、复用上述实际证据。最终Git scope/diff检查与任务提交单独记录；worker完成信号不代表宿主或人工接受。

## 已保留的首轮失败与纠正

1. Maven根目录compile exit1，MissingProjectException；同轮按计划纠正工作目录后compile/package通过，首日志保留。
2. #9旧探针在停售删除后用可信报价读检查原始行，当前#10报价读拒绝剩余停售品，首次exit1但cleanupPassed=true。审查同时发现旧下单断言把客户端packAmount=0当可信费用。只改探针：明确报价拒绝/无写入，删除用具名Mapper查实际行；金额按可信价格×份数+份数+2且HTTP/SQL一致。首日志和机器结果保留并由原wrapper归档，不改生产代码绕过已交付规则。#9纠正复验business/cleanup均通过，首次失败归档 `.scratch/issue9/history/20261014T102528893367Z/`。
3. #13在统计故障检查后首次SQL断言仍期待两份18元=36，省略#10已接受的打包/配送规则。保留 `.scratch/issue21/issue13-batch1-correction.log` 与fresh-ID结果；只将probe期待改为40且pack_amount=2，保留明细/归属/事务断言，纠正复验全部43个实际场景通过，保留首失败；最终复验亦通过。
4. 宿主独立快照Git-less挂载限制在执行前review发现，新增入口改为host版本元数据/Git回退、unknown脏状态并实际断言自检；无安装策略变更。

## 证据状态

- 历史：`docs/yuweiju-minimal-verification.md`及各前置报告可读；10/5六份原始JSON/PNG全项目搜索缺失。只有报告及本轮隔离替代，不宣称旧原文件存在。
- Worker：初轮构建/普通测试及全部适用真实隔离探针通过；Ponytail全diff/callers已审查并退出normal mode，受影响9/13/21最终复验均通过。机器结果仅代表当前任务工作树，精确提交独立验证另行执行。
- 精确提交宿主独立验证：waiting，由host以最终候选SHA fresh构建20分钟复核；新增真正入口可经review加入配置。
- 官方SDK：waiting，实际项目官方compile/正常wx.login/页面截图、正常密码登录浏览器联动不能由worker伪称通过。
- 人工验收：waiting，全部要求/截图/反馈与最终交付状态由维护者填写；没有远端写入授权执行。

## 全要求人工验收清单（绑定同一候选）

候选SHA：待host填写。独立验证命令/结果路径：待host填写。隔离服务启动/24h截止/停止记录：待host填写。官方项目/截图路径与反馈：待host填写。

- [ ] 独立fresh compile/package、admin全检查、两小程序源码build、全部适用probe（含issue21入口）在同一候选通过，最终scope确认。
- [ ] 管理端正常用户名密码登录（错密码拒绝），官方小程序compile与真实wx.login；同一用户新单21元→模拟支付→管理员接单/派送/完成→用户历史/详情与SQL一致，另一用户/匿名拒绝。
- [ ] 正常/缺图/失败/历史97419元及历史图片只读，三端可查看前后官方截图，原素材与视觉保持。
- [ ] 人工客服实际双向WS与归属/关闭；合法AI卡片、失败重试无假卡。host真实模型调用累计≤4，错误边界使用夹具。
- [ ] 热度/分群和风险实际本轮新训练产出、独立窗口/版本/样本/阈值/指标核对；旧active/跳过/无数据不冒充完成；合成效果限制接受。
- [ ] 十组职责和A1–A5、附加失败vs核心回滚、报表/工作台/店铺、SDK安全fixture结论全部核对；历史缺失证据限制明确接受。
- [ ] 记录人工反馈、全部通过时间、服务正常停止及资源保留；最终验证/交付状态明确，远端push/PR/merge/close仅全部接受后执行。

最终复验批次 `.scratch/issue21/runtime-check-batch2-final.json`：9/13/21各exit0；#21 run `joint-Au82zCu8`，91条业务断言，springClosed/httpClientClosed/ownedRowsRemoved均true，原夹具与Redis键值保留。最终bash语法检查和Git-less provenance assert自检通过；生产源码/两前端runtime未变，其他required checks复用代码相同的已通过证据。
