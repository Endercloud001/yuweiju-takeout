# Issue21 三端联动执行报告

候选 `44a40d336caa00dd4a293679c5528517ca1e78fe` 已完成实现、独立验证和六项人工验收。用户明确反馈“1通过/2通过/3通过/4通过/5通过/6通过；全部验收通过”。本票验收服务已停止，卷、模型及证据保留；原数据库未访问。交付集成中，PR/合并结果见后续交付记录。服务于2026-10-14 22:37:54启动，2026-10-15 22:37:54（上海时间）到期；全部验收通过时提前停止，保留卷和证据。

## 候选、范围与预算

起点main `b50afbe396d048e566106e93b48fb67a957828b6`，前置2–20已关闭。第一候选 `8e07cc2554e1ef472e46bcc1b03cdd3f7303068e` 新增#21真实隔离联合探针，校正#9/#13旧费用测试假设，并记录十组职责及A1–A5。第一轮supervisor 10:06:05–11:09:43 UTC，共63分38秒，exit0、一次迭代、有提交、资源已停止。

现场发现主页“配送费6元”与已接受的固定2元矛盾。用户明确批准仅静态WXML例外；第二阶段仅将主页一处文字6元改为2元，提交44a40d3。这是未恢复主页SFC的明确限制，不宣称新增可重建工程。改动脚本、完整Ponytail差异审查、退出normal mode、唯一匹配/逐字节逆向比较/diff检查和提交已保存。保守将本阶段准备及纠正计10分钟（实际提交脚本25.7秒），累计按73分38秒计，不超过80分钟；两阶段，未再次启动supervisor。独立验证仍按原20分钟及获批追加20分钟执行；本机验收准备与人工等待单列，不重置编码预算。

最终相比第一候选仅 `yuweiju-weixin-miniapp/pages/index/index.wxml` 一处静态文案；所有后端、管理端、两小程序可重建源码、探针与运行时代码完全相同。最终范围共八个任务文件，无迁移、算法调优、新依赖或新增hash/冻结contract/baseline/gate。用户原工作区分支和已有变更保留，候选在项目内隔离工作树。

## 独立验证与复用边界

- 首次精确8e07cc2独立验证：Maven编译/包/171测试、管理端fresh安装/lint/type/test/refimg/build、checkout14测试及build、history37测试及build、9/13/15真实探针全部通过。整体exit1：#16固定夹具用户已有1个保留订单，违反其空历史前提；未执行后续17/18/21/doclinks。原失败记录未改写。
- 只读SQL定位旧夹具为1，新独占MySQL/Redis为0；不修改断言、不清旧数据。追加验证重用同提交通过的0–6项及同提交已通过包，在新快照/新库完成16/17/18/21/doclinks，六条命令exit0、资源stopped=true。#21为91条真实Spring/事务/MyBatis/MySQL/Redis联合断言。
- 7/8/10/11/12/14/20保留worker通过记录，按实际源码和探针零差异复用，不称宿主重新执行。十组职责审查与来源见下面表和[worker结果](issue21-worker-results.json)。
- 最终44a40d3由普通Git差异与逐字节比较证明唯一静态变化。后端/管理端/checkout/history与探针检查按完全相同的源码复用8e07cc2准确提交证据；主页新文案经用户再次官方“编译完成”确认后，官方SDK实际元素读取2元与checkout21元通过。不宣称重新全量构建44a40d3。
- [最终复核](verification-evidence/2026-10-14/issue21/final-review.json)、[初次资源](verification-evidence/2026-10-14/issue21/independent-first-resources.json)、[追加资源](verification-evidence/2026-10-14/issue21/independent-resume-resources.json)、[复用说明](verification-evidence/2026-10-14/issue21/independent-reuse.json)、[静态纠正](verification-evidence/2026-10-14/issue21/homepage-correction-result.json)保留完整命令、退出状态和来源。

## 实际本机验收检查

| 场景 | 实际结果/来源 | 人工状态 |
| --- | --- | --- |
| 管理端登录/页面 | 正常密码登录与错误密码拒绝；订单/统计/客服完整路由、中文素材解码、无页面异常/网络失败；browser-readiness-results.json | 待视觉对照 |
| 微信身份 | 两个真正wx.login code经严格prod WechatService交换，同账号user900012稳定、非mock；只有本票复制数据，未访问原库 | 第二真实账号隔离待人工 |
| 同用户21元订单 | 原生主页加18元菜→编译结算21→真实提交1001580→原生模拟支付status2/pay1→正常密码管理UI接单/派送/完成status5→用户编译历史/详情21；SQL一致 | 待人工对照 |
| 历史/缺图/失败 | 当前空历史图、新合成974的404历史图均兜底；管理列表/弹窗/独立详情与小程序历史/详情截图可查看；旧样例19，新单21；UI读取前后SQL金额/图片完全相同 | 待人工对照 |
| AI | 官方页合法HTTP夹具实际卡片；503返回“当前较繁忙，请稍后重试。”，新增卡片0；一次真实DeepSeek请求HTTP200，真实卡片dish900001。真实调用1/4，自动恢复夹具valid模式 | 待人工对照 |
| 人工客服 | 官方用户页面真正WS连接/发送→管理UI收到/回复→官方用户真正WS收到回复，双端截图；完整归属/关闭/事务见独立#15 | 待人工对照 |
| 训练/效果 | 独立#17真实XGBoost/Smile，#18真实LR/RF新文件/激活/评分；完整窗口/样本/阈值/旧active与失败状态记录见实际结果 | 合成效果限制待接受 |
| 安全/回滚/职责 | 匿名admin与user保护入口实时401；跨用户拒绝/附加降级/核心SQL回滚/上传路径等复用具名探针与普通测试；无原库写入、无真实OSS上传 | 待清单确认 |

**历史证据限制**：10月5日六份原始JSON/PNG全项目搜索缺失。当前974是明确新建的隔离历史19元样例，复制本票1001580的业务字段并设定历史时间/19元/失败图片，归属真实微信用户900012；它不是原始974，不证明原历史素材或原数据库内容。独立探针历史模拟与本机样例均明确标识合成。历史读取后SQL原值完整保留，见acceptance-history-fixture.json与final-review.json。

独立热度产出 `analysis-heat-xgb-2040-06-29-1791988087203`：347训练/33评估，11用户快照与分群行、3群、5菜品快照及预测；RMSE0.0029、MAE0.0020，对原0.30/0.20比率阈值通过。风控 `order-risk-20261014223017-1dae9f8a`：181训练/59holdout、正样本90/30，新LR/RF文件保存/metadata/激活成功。均为本票真实算法在合成数据上的执行，不推导真实营业准确率；未改变阈值。模型目录在acceptance-runtime，只复制独立验证本票产物，不覆盖原模型。

## 首次失败、纠正与资源

首轮Maven cwd错误、#9/#13旧假设、初次#16夹具失败、追加准备WSL cwd错误均有原记录。服务初次admin快照缺vue-tsc和Docker只读mount错误，纠正后运行；不是业务源码修复。页面驱动首次登录未触发请求、查询翻转按钮名称选择、接单/派送后弹窗自动关闭、两个“关闭”按钮、客服错误容器选择及历史兜底路径假设均为驱动错误，已按真实模板修正。每次先核对当前订单持久状态，未重复下单/支付/接单。AI第一次驱动错误要求503无文字回复，而产品允许明确繁忙提示；修正为“无假卡片”，保留首结果。

官方截图曾出现SDK超时/保存空间上限。只读getSavedFileList为空，未清任何存储；原失败保留。尝试具名窗口PrintWindow得到黑图/空白/错误窗口布局，均不作为业务截图证据。重新打开本票官方项目后SDK截图恢复，成功取得官方AI/客服/历史/新旧详情截图；最终以官方SDK实际路由/数据及成功图片绑定，原失败不被改称成功。交接前主页补拍再次SDK超时，新文案2元的官方元素读取通过但新主页截图未取得；结算采用同源码8e07cc2首失败时成功补拍的21元截图，44a40d3结算另有真实编译与落库通过证据。追加补拍没有下单/支付/清存储，人工需核对实际主页2元。

coding及两个独立验证容器均停止。全部本票acceptance backend/gateway/admin/model与fresh独立验收fixture依赖已停止；名称/网络/卷/到期与停止入口见[服务状态](verification-evidence/2026-10-14/issue21/acceptance-state.json)。原coding fixture已在交接前停止并保留卷，停止入口同时覆盖两套fixture，不删除卷。保留期限与全部通过后自动停服务已获授权。原凭据只在host内存/只读模型配置挂载供严格微信与限制1/4模型调用，无凭据值进入报告/截图/日志。

## 全Loop复盘（宿主一次）

先检查coding与两次独立验证stopped记录，再阅读result/progress、最终diff及失败证据。未启动递归复盘或维护任务。以下均为建议，未实施为新框架/新gate。

| 证据 | 影响 | 建议 | 实施状态 |
| --- | --- | --- | --- |
| 首#16旧用户残留1单，独立#16需要0单 | 干净代码也会因fixture顺序失败 | 每次独立验证使用本票fresh schema或明确每个探针前提；复用现有SQL工具 | 本票fresh依赖已用；通用维护未实施 |
| Windows Git不能解析WSL worktree.git绝对路径；追加launcher cwd无效 | 准备中断、跨平台Git操作失败 | 用已选WSL runtime及显式绝对cwd，文件读取仍项目内 | 本票入口纠正；通用维护未实施 |
| SDK截图保存限额、旧连接/导航时序；驱动假设页面行为 | 不能用tap返回/route推定支付或交付 | 界定超时，先读真实API/SQL后重连；先检查WXML/实际DOM并保留首失败 | 本票已采用；不新增通用状态机 |
| 原始10/5六份材料缺失 | 原历史视觉无法复验 | 长期把脱敏证据放受控项目目录并明确来源；禁止复写原库造旧证据 | 仅记录限制，未重建旧证据 |
| 静态主页6元与固定2元不一致且无SFC | 视觉误导，生成源修复受限 | 本次明确许可只纠正文案；若后续主页行为需改，再单独恢复来源 | 本次文字已纠正；源码恢复未实施 |

## 人工与交付

全部必需项、URL、账号、截图与反馈格式见[一页验收清单](issue21-acceptance.md)。人工等待不计编码时间。用户已针对同一44a40d3明确反馈六项全部通过；根据清单内容记为第二真实账号隔离、人工视觉及所列限制均经维护者确认，机器自动检查仍只证明本报告所列范围。未增加自动第二真实账号检查或补拍成功断言。按既有授权进入yuweiju-deliver，必要本地提交、push/PR/merge/sync/close；服务已按约定停止。精确反馈见[人工记录](verification-evidence/2026-10-14/issue21/human-acceptance.json)。

## 十组职责与A1–A5（已审查的worker来源）


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
| A1 金额一致 | #10/#21新单18+1+2=21，多数量/菜品套餐/非法金额/可信费用；checkout源码和admin消费者回归，SQL同用户/admin详情一致 | #21隔离合成974保留19；原始974原材料缺失，本轮官方截图已取得、人工对照待反馈 |
| A2 模型失败语义 | #16真实本地HTTP合法输出、畸形/非法候选/402/503/超时，失败无卡、天气缺失不阻断、在售再校验 | host真实模型1/4次成功，fixture不是业务效果验收 |
| A3 历史图片 | #12用户历史/详情、admin页/详情，当前图B与历史图A/空缺图不写回；#21同一链路历史图只读；两源码及admin缺图回归 | 当前官方及管理端兜底截图已取得，布局人工对照待反馈；缺失六份旧原材料不恢复 |
| A4 智能故障隔离 | #13推荐统计/附加风控故障不阻断核心，下单核心SQL故障完整回滚；#14风险附加不可用不伪LOW，风险筛选失败不退无筛选 | 隔离MySQL/Redis/HTTP/文件；SQL不撤销缓存/文件/自增 |
| A5 分析执行状态 | #17新XGBoost/Smile输出、#18新LR/RF文件/激活与指标；无数据/失败/旧active不冒充新训练，非法参数友好拒绝 | 合成样本证明执行机制，指标/阈值通过与业务效果分别记录 |



## 人工入口401复核（2026-10-14）

用户反馈打开管理端直接进入工作台后出现401，尚未人工全项通过。六个必需服务均运行、MySQL/Redis healthy。真实宿主18321页面、代理登录、工作台与订单API均通过；合成失效token Cookie可稳定复现根路由进入/dashboard、六个API401与相同弹窗，无Cookie进入/login且没有401。代码读证据：user store Cookie键为token/userInfo；router只判断token是否存在，不判断有效性。Cookie以主机隔离，127.0.0.1的不同端口共用该会话位置；旧会话过期/注销也会触发同类现象。用户浏览器具体token未读取或记录。

本票验收入口改为http://issue21-20261014.localhost:18321/login；仍为127.0.0.1绑定的同一Vite预览及代理，无凭据/Cookie全局清理、无修改生产代码、无容器重启、无扩大编码轮数。已在同一真实浏览器上下文保留另一个主机的合成失效Cookie，验证独立域名不读取它；正常用户名密码登录，工作台、订单、统计、客服22个API响应无401。宿主原端口Host请求200，浏览器容器因.localhost回环语义采用显式解析到同一验收admin容器、5173内部端口；用户入口为发布的18321。第一次浏览器复验在SPA工作台请求尚未到达时提前断言失败，保留early-load-failure；改为等待实际业务响应再断言后通过，不把驱动等待失败当业务修复。

证据：.scratch/issue21-afk-20261014/service-recheck-http.json、entry401-reproduction.json、entry401-stale-session.png、isolated-entry-recheck.json、admin-isolated-host-dashboard.png。用户随后反馈六项全部验收通过，按该入口清单记录为通过；原入口旧会话/生产应用的通用过期会话处理未改，保留为明确限制。服务期限及候选44a40d3不变。

## 验收后证据归档与停机

已将脱敏的实际宿主结果、首独立失败/追加成功资源及可查看截图归档到`docs/verification-evidence/2026-10-14/issue21/`，供fresh clone评审。完整本机日志和未采用截图仍在.scratch；未提交配置、原凭据、token、模型文件或数据库数据卷。机器JSON中的waiting是当时快照，不被改写成自动人工通过；当前人工结论以human-acceptance.json为准。

Docker停止确认全部八个本票容器均Exited；MySQL/Redis为0，后端/admin为143，model/gateway为137（Docker停止宽限期后强制终止），没有运行训练或业务写入；数据卷保留。到期worker会按stoppedAt退出，不另杀复用PID。最终新增归档仅文档/证据，不改变已验收业务行为，受影响文档链接及文件脱敏/范围另行复查。
