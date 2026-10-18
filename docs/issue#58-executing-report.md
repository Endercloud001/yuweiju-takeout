# Issue58：结算仅立即配送、服务端估时只读

范围为主题02 F8 / #58，起点 `9674d3ad2c190956e78dd85fdb4436b86838302b`（#57 已合并关闭，#55/#56 已交付）。本报告随任务候选提交保存，精确候选以 Git 提交记录为准。最多2次 supervisor iterations 共60分钟，closing2分钟在内，截止 `2026-10-18T15:49:18.288Z`；重复运行称 check batches，未调整模型、认证、预算或安全策略。

## 实现与三端影响

- 小程序：[Checkout.vue](../yuweiju-weixin-miniapp/checkout-source/src/Checkout.vue) 删除真实配送时间点击入口、箭头和预约弹层，展示“立即送出 / 预计送达：服务端值”。[checkout.js](../yuweiju-weixin-miniapp/checkout-source/src/checkout.js) 请求固定 `deliveryStatus:1`、`estimatedDeliveryTime:null`。普通未来或跨日估时只展示，不解释为预约。调用者复核后删除仅预约使用的日期列表、创建时日期初始化及选择处理器；餐具弹层、地址/备注往返、身份、价格刷新、表单/错误/重试、支付跳转与生命周期保留。`pages/order/index.js/index.wxml` 由真实 build 生成，未手改生成文件。
- 后端：[OrdersApplicationServiceImpl](../yuweiju-backend/src/main/java/com/codeying/service/impl/OrdersApplicationServiceImpl.java) 仅在 submit 创建新 Orders 时固定状态1；DTO 旧 `Integer deliveryStatus` / `String estimatedDeliveryTime` 仍可解析，旧预约0兼容接收。估时仍用已有 `calculateEstimatedDeliveryTime`（备餐10分钟 + 地图配送分钟），不信任客户端时间；不引入日期/调度、迁移或新规则。#57 参数后/写入前 ShopStatusService 检查、prod/dev 原策略、事务、计价与份数打包费均保留。
- 历史与管理端：[OrderAssembler](../yuweiju-backend/src/main/java/com/codeying/assembler/OrderAssembler.java) 将原 `deliveryStatus/estimatedDeliveryTime` 复制到两端 VO；`OrdersMapper.findOwned/findHistoryPage` 保持读取，未改写历史。小程序 `pages/details/index.wxml` 的期望时间依原状态展示；管理端 `src/views/order/index.vue/detail.vue` 保留原预计送达时间展示与 `src/types/order.ts` 类型，无需修改。现有消费者不要求拒绝旧请求，遵循已确认的兼容归一化策略。

当前[用户订单 API](../yuweiju-document/api/余味居-用户端接口.html) 已同步说明旧字段可省略/解析、新单立即配送、服务端重算估时、跨日不代表预约、历史不变。数据库 `delivery_status=0` 历史含义和 schema 保留；无需迁移。回退通过普通 Git 回退本票源码/生成页/API说明，部署时协调页面与服务端语义，避免只恢复误导预约 UI；已创建新单及历史不作数据回写。

范围不含 #59 无效购物车、#65 催单、#66 AI、预约调度、真实付款、原库写入、发布或外部微信/地图/天气/OSS/模型调用。原环境配置、全局 profile、认证、共享 vendor/runtime/store、管理端源码和其他页面保留。

## 检查批次与证据

所有命令 cwd `/home/agent/workspace`，非登录 `bash -c`；确认 java `/opt/java/openjdk/bin/java`、mvn `/usr/share/maven/bin/mvn`。临时文件仅 `.scratch/issue58`；测试 JAVA_TOOL_OPTIONS 只指定本批次项目内临时目录。命令/时间/cwd/退出按批次保留在 `commands.jsonl`，无 token、payload 或原数据快照输出。

初始 batch `.scratch/issue58/initial`：ci exit0，test exit1，15/18。旧构建测试和运行时测试并行，运行时先加载旧生成页，产生旧配送0与旧WXML绑定；源码编译模板检查通过。首次失败 `2.log` 保留。纠正为运行时测试自身 before hook 等待真实 compileCheckout 后再创建 harness，不删除或减弱断言；同一 supervisor iteration 内重跑原必需命令序列。

必需顺序：

```sh
npm --prefix yuweiju-weixin-miniapp/checkout-source ci --no-audit --no-fund
npm --prefix yuweiju-weixin-miniapp/checkout-source test
npm --prefix yuweiju-weixin-miniapp/checkout-source run build
mvn -B -f yuweiju-backend/pom.xml compile
mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue58-check.sh
python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root /home/agent/workspace .sandcastle/task-progress.md 'docs/issue#58-executing-report.md'
```

纠正后初检 ci/test18/build/compile/package176 通过。首轮真实探针在响应ETA与SQL时间直接字符串相等断言失败（`.scratch/issue58/sql-lSHhZ8Uj`）；此前 legacy0 实际HTTP、新SQL立即配送/可信40元/17分钟重算、关店/不可读/归属/历史读取均通过，finally清理和Spring/HTTP关闭通过。保留首次失败，按既有+08时间格式解析为instant、允许SQL DATETIME(0)与JSON省略毫秒的最多1秒精度差；服务端策略仍精确17分钟，历史整行/ETA仍要求原值不变。完整探针纠正重跑 `.scratch/issue58/sql-nfusnNW8` exit0，122条断言，businessPassed/cleanupPassed/springClosed/httpClientClosed=true，failure=null。命令记录 `.scratch/issue58/probe-corrected-command.json`；初检 Markdown 相对链接检查 exit0（`initial-doc-links.log`）。首败原始响应body未保存，不将精度推断写成已抓取到首败原始时间值；纠正后验证按现有字段精度通过。Ponytail后最终检查另行追加。

普通测试新增：旧JSON预约0、正常1、省略字段均生成立即配送；旧客户端时间不影响服务端估时；用户/管理员历史 VO 保留0和原估时。原 #57 普通测试、所有构造器夹具、既有费用/状态/认证回归保留。小程序18项包括真实生成页的旧 Vue/store/API、uni 编译渲染函数，以及 editable template 经现有 Vue compiler 实际渲染；检查只读送达行无事件/箭头、无预约弹层，真实生成 WXML 按 AST 检查事件/组件，再通过实际 `__e` 分发去支付事件，未来23:59和跨日文本估时仍发送1/null。原表单/价格/地址/餐具/鉴权/网络失败/打烊重试场景继续执行。Node 回归不冒充官方工具。

## 自包含 SQL/Redis 探针

[Issue58Probe.java](../.sandcastle/environment/Issue58Probe.java) / [issue58-check.sh](../.sandcastle/environment/issue58-check.sh) 复用已审查 #57/#55/#10 模式。脚本先等待 package 成功，才从实际 Boot jar 提取依赖并 javac；每次新 `.scratch/issue58/sql-XXXXXXXX`，新UUID名称和数字ID，不依赖 worker 旧行/缓存。standalone `application-afk.yml` 加启动参数强制 internal `mysql:3306/sandcastle_fixture`、`redis:6379/db0`，禁用 scheduled processor、模型/训练/analysis/risk任务；仅本地 map seam 固定7分钟，无外部请求。

写入前验证专用数据库、900001 user/employee/category 合成标记、全部实际表PK/列/索引/约束、核心表InnoDB，确认UUID各ID与清理归属范围空白。SQL全部表按真实PK排序的行快照与Redis DUMP/绝对过期时间只保留内存，不落盘原数据。真实 prod ShopStatusServiceImpl 通过既有 `/admin/shop/0` 拒单与 WRONGTYPE 状态不可读拒单，检查零核心写入、完整SQL/购物车不变；再用 `/admin/shop/1` 打开，认证/归属不变。HTTP→实际 Spring代理事务→MyBatis/MySQL，不以 mocked Mapper 宣称SQL正确。

实际 POST `/user/order/submit` 分别传 legacy0、ordinary1、省略标记，legacy0带旧时间 `2001-01-01 01:01:01`，普通请求时间为null，均带错误报价；应接收，SQL新行状态1、可信40元（18×2+2打包+2配送），估时精确为 order_time+17分钟，响应按既有秒精度与SQL相符（最多1秒差），仅当前购物车清空。历史reserved0固定估时 `2020-01-02 08:30:00`，详情/分页读前后及三次新提交之后原整行/明细完全相同；他人车/历史和越权拒绝保留。finally只清理自己UUID归属行、恢复临时占用status的DUMP及剩余TTL，再比对原SQL/Redis，关闭自有Spring/HTTP客户端。无全局清理、DDL或原库；自增序列可能前进，不声称序列回退。MySQL/Redis服务本身由宿主生命周期管理。

## 宿主独立验证与官方交接（待执行）

本 worker 不写宿主独立或官方通过。独立 verifier 应对精确候选新快照执行上述必需命令（ci在检查中，probe等待package，不使用worker缓存/行），检查本票API/范围和新UUID清理结果。未修改生产submit的#57营业检查，可复用 [#57执行报告](issue%2357-executing-report.md) 的精确来源 `9b3c2bff3592ee9bd468fc05f3b61b49361a3607` 及宿主独立/官方证据；本票探针仍针对关闭和状态不可读重测提交边界。

就绪输入 `official-readiness.json`：Tool2.02.2608080 / SDK3.17.4，官方隔离项目自动化9458、实际API18559、index路由、native PrintWindow截图已核实。仅就绪，不是本票业务验收。SDK saveFile存储限额仍披露，沿用已核实官方窗口 native PrintWindow；不删除其他任务存储。

官方所需场景（宿主应在精确候选项目重新编译）：

1. 新UUID自有用户/地址/菜品18元×2购物车，使用真实prod既有管理员开关OPEN；认证保持，map只用本地固定7分钟 seam。导航 `/pages/order/index` 后获取 fresh Page；确认可见“立即送出”和服务器预计送达文本，`.bottomTime` 及后代没有tap事件，`.bottomTime .to_right`、`[data-ref="timePopup"]`、`.pickerCon`、`.dayBox`、`.timeBox`、`.scroll-row-item` 和原生 `picker` 均不存在（餐具 `pikers` 应保留）。对只读行做一次普通tap，确认无弹层，餐具入口仍可用。
2. 实际 `/user/order/estimatedDeliveryTime?addressBookId=自有地址` 估时与页面相符。需要跨日时仅调整宿主隔离map seam使服务端返回跨日估时，保留服务端计算路径，不以客户端setData替代可信估时验收。Node已有未来/跨日展示回归。
3. 真正点击一次“去支付”：捕获真实受保护POST，`deliveryStatus=1`，进入 `/pages/pay/index?orderId=新ID` 后重新获取Page对象；读真实SQL新单1、服务端ETA、40元/明细与仅本人车清空，检查实际支付页金额交接。tap回执超时先读持久状态，不重发提交。保留原失败，不用成功路由单独证明SQL正确。
4. 新鲜历史reserved0及固定原ETA夹具（参考探针seed）；分别进入 `/pages/historyOrder/historyOrder` 和 `/pages/details/index?orderId=历史ID`，每次导航后获取fresh Page；历史列表保留原金额/状态和详情入口，详情的原“期望时间”显示历史ETA，真实详情/历史API保留0和原时间。在新单提交前后只读比对历史整行/明细和其他用户车。不要用改写历史来构造立即订单。
5. 若宿主复测关闭/不可读失败：一次按钮事件，toast可理解、地址/备注/餐具/价格/购物车保留，确认没有新SQL行；开店后重试用新事件，并先检查上次持久状态。实际认证/归属、历史数据和服务资源清理结果单列。

host independent / official / delivery 均 pending；逐票人工验收由用户明确免除，但真实业务和官方工具检查仍必须完成后才可宿主交付。worker 无 GitHub写入、push、合并、部署或监督器重启。

## Ponytail full 收尾复核

初检通过后读取挂载 `/home/agent/task-input/.agents/skills/yuweiju-ponytail/SKILL.md` 与完整上游 Ponytail5.1.0（commit `9b58c1ffb790c075ca32e70a89cf1d80588f4abf`）。从配置 startCommit `9674d3ad2c190956e78dd85fdb4436b86838302b` 复核所有已跟踪差异与3个任务未跟踪源码/文档文件；无之前任务提交或已暂存改动。完整diff留于 `.scratch/issue58/ponytail-full.diff`；长HTML/WXML另对照源码和AST，调用链/DTO/VO/Assembler/Mapper、当前API/数据库、管理端两个历史视图与类型、历史可重建源码、认证/配置、相关普通测试和真实探针逐项复核。

采用：删除新探针未使用的异常导入、删除与 `estimated.equals(ordered.plusMinutes(17))` 重复的分钟差判断（保留更严格的精确17分钟断言）、去掉删除预约块后遗留的空行；官方交接明确实际缺席选择器和历史列表/详情各自展示内容。拒绝：新建配送策略层/默认值框架、移除兼容DTO字段或历史0含义、重写共享vendor/样式、删安全/快照/事务/TTL恢复检查、改原生产profile或关闭已有测试。这些都不帮助本票且会损害兼容、安全或可重建性。

已明确 `stop ponytail / normal mode`。已在同一原预算内重跑全部必需命令通过，随后检查范围并只暂存本票14个文件，沿正常Git guard保存本地任务候选；缺少宿主Git身份时仅本次commit使用 `-c user.name=Codex -c user.email=codex@localhost`，不改全局配置。宿主独立/官方仍pending。

## 最终 worker 候选结果

明确退出 Ponytail 后，最终 check batch `.scratch/issue58/final/commands.jsonl` 六条必需命令按原顺序全部 exit0：ci、checkout **18/18**（0跳过）、真实build、Maven compile、package **176/176**（0失败/错误/跳过），以及自包含 `issue58-check.sh`。脚本自己的 package176 → Boot依赖138 → javac → probe 同样全部 exit0。最终新UUID运行 `.scratch/issue58/sql-EiXbclCT` **122条断言**；businessPassed/cleanupPassed/springClosed/httpClientClosed=true，failure=null。全原SQL行、无关Redis、原status DUMP/剩余TTL及历史reserved0/原ETA均按上述范围验证保留。汇总 `.scratch/issue58/checks-summary.json`。

首次Node加载顺序失败和首次SQL时间比较失败保留，未删测试、未改生产估时策略，也未扩大轮数/时间或重启监督器。最终检查后只更新本报告/进度并运行Markdown链接与Git范围检查，没有再改生产/测试/探针源码。Ponytail同时审查了本票 scratch 生成/检查助手与日志作为证据；它们和临时依赖/类文件不进入暂存。14个授权任务文件随正常本地候选提交保存；精确SHA在Git和 `.scratch/issue58/candidate.json`，避免报告自引用提交号。

自有Spring/HTTP客户端均已关闭；MySQL/Redis容器及官方工具项目由宿主继续执行独立/官方验证并按既定生命周期停止，worker未擅自停止共享宿主资源。无当前编码阻碍、无Git安全拒绝；host independent / official / delivery仍pending，用户免除逐票人工验收不代替这些必要检查。

最终 Markdown 相对链接检查 exit0，记录在 `.scratch/issue58/final/commands.jsonl` 和 `doc-links.log`。首次显式 `git add` exit128，Git报告已有index.lock；已保存 `stage-first-failure.json`。只读检查仅见一个早先的zombie Git，不能据此确认宿主锁来源；未删除锁/终止进程/更改策略。等待后沿同一Git guard重试原14文件命令 exit0，属于正常暂存恢复，不是绕过拒绝。补记本段和进度后仅重查Markdown与暂存范围。源码/测试/探针仍是上述全部通过版本。
