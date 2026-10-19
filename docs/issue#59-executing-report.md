# Issue #59 执行报告：启动准备失败，队列停止

> 前半为历史启动准备记录；本次 worker 实施、Ponytail、最终检查与候选记录追加在后文。历史失败结论不代表本次 worker 状态。

2026-10-19（Asia/Shanghai）。按 原队列交接（`afk-queue-handoff-2026-10-19.md`，历史宿主材料，未挂载于本候选） 与本轮明确恢复授权，从 #59 顺序继续，继承每票最多 2 轮、编码共 60 分钟（含 Ponytail）、独立验证 20 分钟、gpt-6.1-sol / medium 和失败即停规则。逐票人工验收免除，真实业务和官方工具验收要求仍然适用；原库、Release、正式部署不在范围内。

## 当前结论

**#59 在启动准备中失败，未启动 AFK；#60—#66 未启动。** 没有候选提交、独立验证、业务验收或 GitHub 交付。编码消耗 0 轮 / 0 分钟，独立验证预算未使用。遵守失败即停，没有重跑失败构建、重启监督器、跳票或扩大预算。

当前本地与远端 main 均为 `764b769b8fb018175e3a017b87d390f6ed21081c`。#59 正文、评论、OPEN 状态及原生 blocking dependencies 已读取；无评论、无原生阻塞项。本票要求展示失效购物车条目、主动移除后恢复结算、提交重新严格校验，不能自动清车或只提交有效子集。后端与小程序联动；管理端只是停售来源，不新增管理功能。API 兼容扩展与回退资料需随未来实现同步；本轮未修改三端业务代码或接口。

## 准备与实际证据

全部新产物位于项目 `.scratch/issue59-afk-20261019/`。从当前 main 创建独立 runtime，保留根工作区用户变更；旧 runtime 和 SDK 路径已被清理，未照抄为可用环境。通过当前 `prepare-input.py` 保存本票及 Ponytail 材料，并复制主题规格。配置为准备草稿，未交给监督器运行；其中 `issue59-check.sh` 是待实现的验收入口，当前不存在，不宣称已经执行。

- WSL Ubuntu、Node v22.23.3 路径和 `sandcastle:yuweiju-dev-git-safe` 镜像已实查。runtime `npm ci` 和项目内官方 SDK 安装退出 0；未改变模型或认证设置。
- 本票独立 MySQL/Redis 合成夹具启动及健康检查通过，数据网络 internal，出口为本票具名网络。当前 preflight 包括专属只读登录检查，`passed=true, exitCode=0, classification=ready, stopped=true`。来源 `.scratch/issue59-afk-20261019/preflight/preflight-1792361650676933735/`。
- 官方项目为本票 `miniapp/`，API 指向本机隔离端口 18561，原外部 websocket 地址在隔离副本指向本机。`cli islogin` 返回 true；SDK 实际读到 Tool `2.02.2608080`、SDK `3.17.4` 及 `pages/index/index`。证据 `official-readiness.json`。最初当前页尚未生成的 SDK 错误保留，随后在同一次有限就绪探测内取得有效页面。
- 上述仅证明工具和页面就绪。真实隔离后端 API、合成登录会话及实际业务页面数据尚未准备完成，**没有启动依赖这些能力的编码任务**。首页仍为旧生成产物，后续必须明确其可重建编辑来源和全部购物车消费者，不能手改产物冒充完成。

## 失败命令与位置

宿主通过 WSL Docker 在镜像内以 UID 1000 执行，工作目录 `/candidate`，绑定本票 `runtime/`。先执行 `mvn -B -f yuweiju-backend/pom.xml compile`，退出 0；随后 `mvn -B -f yuweiju-backend/pom.xml package`，退出 1。日志分别为本票 runtime 内 `prepare-compile.log`、`prepare-package.log`，Surefire 报告保留在 `yuweiju-backend/target/surefire-reports/`。

176 项测试中 1 项失败、0 错误、0 跳过：`Issue19ReportExportTest.workbookPreservesServicePeriodAmountsAndNumericDailyCells`，测试第 39 行预期 HTTP 200，实际 500。日志记录 `AdminReportController.export` 第 56 行抛出 IllegalStateException；对应源码在 classloader 无法取得 `template/运营数据报表模板.xlsx` 时抛出“报表模板不存在”。根因尚未确认，不能据此断言是代码回归、资源遗漏或容器编码环境问题。未通过删除测试、跳过测试或修改业务来绕过。

更早一次准备构建因日志重定向到未挂载的 `/candidate/../` 而被拒绝；该次 Maven 未开始。保留该命令退出 1 的会话证据，纠正为映射内路径后才执行上述 compile/package。准备脚本也曾假定 Git 中存在 `project.private.config.json` 而报 FileNotFoundError；没有改变用户私有配置，后续通过实际 SDK 页面取得就绪证据。两者均发生在编码启动前，不计为 AFK 轮次；最终停止原因是实际 package 测试失败。

## 清理、交付与后续

本票 `docker compose stop` 退出 0，MySQL/Redis 均停止；官方工具仅关闭本票隔离项目，退出 0。复查本票运行中容器为 0，独立 runtime 的 tracked diff 为空，根 main 保持原提交。数据卷、网络、输入与日志保留以便核查，无原库访问或写入。结构化记录见 `.scratch/issue59-afk-20261019/stop-result.json`，队列状态见 `.scratch/queue55-66-20261018/status.json`。

本次没有 AFK Loop，因此没有 Ponytail、候选验证或交付可复盘；不把准备日志当作业务完成。具体待处理项是核查报表模板在该 Linux 容器的资源路径与加载环境，保留当前失败证据；尚未实施任何环境或测试修复。失败处理及明确恢复授权前，不自动继续 #59 或启动 #60—#66。

## 2026-10-19 报表模板阻塞修复

用户另行授权排查并修复报表模板问题。本节更新上述“根因尚未确认”和“未修复”的历史状态；队列仍停止，未启动 AFK，也没有业务候选或交付。

根因是宿主准备构建命令漏传 `LANG=C.UTF-8`、`LC_ALL=C.UTF-8`。实际 Git-safe 镜像默认 LANG/LC_ALL 为空、locale 为 POSIX；JVM `file.encoding` 虽为 UTF-8，但 `native.encoding` 和 `sun.jnu.encoding` 均为 `ANSI_X3.4-1968`。中文命名的模板存在于 Git 和实际 `src/main/resources/template/`，但该环境下 Maven 没有将其复制到 `target/classes/template/`，最终 Controller 读 classpath 资源失败。不是模板未提交，也不是报表字段或业务规则问题。

在同一诊断容器、同一 checkout 和依赖下，原 locale 执行 `mvn -B -f yuweiju-backend/pom.xml -Dtest=Issue19ReportExportTest test`，退出 1，2 项中同一项失败，日志 `runtime/report-repro-network.log`。随后仅给进程增加两项 UTF-8 locale 环境变量，执行同一测试（已下载依赖，使用 Maven `-o`），退出 0，2 项全部通过，日志 `runtime/report-fixed-test.log`；`runtime/report-fixed-java.log` 证实两项 JVM 路径编码均变为 UTF-8。最早一次离线尝试因新容器未有 Maven 缓存而未进入测试，日志 `runtime/report-repro.log` 保留，不算复现成功。

修复保存为项目内本票 `.scratch/issue59-afk-20261019/prepare-backend.ps1`，显式传入两项 UTF-8 locale，日志输出到正确挂载的本票目录。实际执行此入口退出 0：compile 成功、package 成功，176 项测试全部通过、0 失败/错误/跳过。日志为 `fixed-compile.log` 与 `fixed-package.log`。检查实际 Boot jar 的 `BOOT-INF/classes/template/运营数据报表模板.xlsx` 存在且内容与源码模板完全一致，结果保存到 `template-fix-result.json`。

现有 `preflight.py` 和 `verify-task.py` 原本已经显式传入这两项变量，不需要改动；不新增 gate、改测试、改模板名称或改业务代码。三端业务、API、schema 与数据均未修改。此次错误属于宿主准备命令遗漏，修复范围为本票准备入口及证据记录；不重建共享镜像、不改全局环境。诊断与构建容器已停止移除，原隔离 MySQL/Redis 继续保持停止。队列记录已标注该阻塞修复，但恢复 #59—#66 仍需明确指令。

## 2026-10-19 队列失败处理规则更新

用户将本队列原“失败即停”改为：“失败先由 Agent 自行排查并解决问题，若排查和解决过程仍失败再停止”。已同步交接文件及队列 `stopPolicy`；上文按旧规则停止的记录保留为历史事实。允许既有授权和剩余额度内的排查、修复、复验与必要技术恢复，不重置预算、不跳票、不绕过安全或人工决策边界。本次只更新规则，未启动 AFK 或恢复队列。

## 2026-10-19 Worker 实施（本次授权，进行中）

上文启动准备失败、根因与宿主 locale 修复均为历史记录，完整保留。本次从 `764b769b8fb018175e3a017b87d390f6ed21081c` 开始实施 #59/F3；容器项目根 `/home/agent/workspace`，非登录 bash，原截止时间 `2026-10-19T05:25:40.005Z`，最多 2 supervisor iterations、总 3600000ms（含 Ponytail），没有重置预算。这里只记录 worker 证据，独立 verifier、官方工具、人工与交付状态由宿主另行追加。

### 实施范围与兼容

- 后端 owned cart GET 改为逐项检查，返回原数组和原身份/数量/口味/历史展示字段，新增 `available`、`unavailableReason`；失效 `amount=null`。只捕获商品业务校验异常，归属失败和数据源故障仍整体拒绝；不写数据库、不自动删项。严格 submit 再次读取商品状态/可信价格，原事务保留。
- 小程序新增 [首页局部来源](../yuweiju-weixin-miniapp/home-source/README.md)：原生 WXML、配对 uni render、样式/配置与窄行为适配器，构建精确写入首页四文件。保留 vendor module20 的登录、目录、导航、规格及 module12 store/module24 authentication 请求，不重写公共 vendor。失效行显示原因、隐藏金额/增项、总额显示“待恢复”，结算提示逐项移除；减号调用现有 owned sub，减至零才消失。
- 结算可编辑来源保留全部行及失效原因，隐藏可信总额，阻止 submit，提供返回购物车入口；提交前刷新仍保留。历史明细与再来一单来源无需改变：实际 history/details 不请求 live cart GET，详情写共享 store 后，首页 onShow/init 与 checkout refresh 重读实际购物车。未更改历史图、模拟支付或历史订单金额。
- 管理端无此 cart GET 消费者，仅现有停售接口触发状态变化，无新增功能。无 schema、主键、原数据迁移；接口与回退说明已附入用户 API 的 `issue59-cart-recovery` 段落。回退需协调客户端/GET，严格 submit、认证和事务不得撤销。

### Worker 检查批次与保留失败

命令均在容器项目根运行，日志在 `.scratch/issue59/`，重复执行称 check batches。`java` 和 `mvn` PATH 已实查；worker 已有 C.UTF-8，验收脚本同时显式注入，不修改报表模板/Controller/旧测试。

1. 首页 install/build 退出 0。测试早期两批退出 1，分别为测试适配器处理引号内 `>`、Vue 隐藏弹窗 null 访问与 WXML null-safe 行为的差异；保留 `home-test-batch1.log`、`home-test-batch2.log`。修正测试适配后 batch3 退出 0，5 项通过，随后补充菜品/套餐与口味、pre-fix legacy 控制，最终复验待记。
2. checkout 必需 install/test/build batch1/2 失败日志保留：旧 uni 编译器会把 `Number(obj.amount)` 错误提升到循环外，也会提前计算隐藏行的 `null.toFixed()`；新实际运行时回归发现后，改用 null-safe 行格式化，真实模板分支仍只显示原因。batch3 全链退出 0，21 项通过，包括三类失效实际模板、MP render、事件绑定、无 submit/clean、返回首页。没有删除/跳过断言以掩盖业务错误。
3. history 必需全链及后端全量 package/隔离探针正在执行。后端第一次下载锁定 POM 依赖，尚未宣称 SQL/事务通过。

### 官方工具后续场景与选择器（交接，不代表通过）

宿主使用精确候选构建至已准备的官方隔离项目与本机 gateway。只使用本票合成认证用户和独立随机 ID，不使用原库；下列身份/金额为场景说明，不要求固定数据库 ID。

- 为合成用户准备 A（在售、当前价 18、购物车快照价 0.01、数量 2、口味 mild、历史图）及 B（数量 2、保留历史名称/图片）。分别覆盖菜品/套餐停售、删除；非法目录价格使用隔离直接夹具。首页 `pages/index/index` 点击 `.orderCar` 打开 `.cart_pop`，断言两项名称、图片、`.dish_number` 数量、`.cart-invalid-reason` 明确原因；`.order_price` 为“待恢复”，无 B 金额/可信部分小计，B 无可见 `.dish_add`。
- 点击 `.order_but`，保持首页，提示“请先移除失效商品后结算”；无 submit/clean。通过 B 行 `.dish_red` 点击两次：第一次 B 数量变 1，第二次仅移除 B；A 的身份、数量和口味保持。总额恢复 A 商品小计 36，点击结算进入 order 页，合计为 40（商品 36+打包 2+配送 2），使用已有地址/备注/餐具流程。
- 直接进入结算仍有 B：`.cart-invalid-reason` 原因可见，`.totalMoney`/`.order_price` 显示 `--`；点击 `.order_but_rit` 不得发 submit/clean，`.cart-problem` 返回购物车恢复。
- A 已在结算页读为可购买后，由现有管理端停售 A，再点击支付：若 refresh 已发现，客户端阻止；若状态变化发生在刷新后/真正提交前，服务端严格拒绝。核对无新订单/明细且 cart 不丢，重新起售后再确认可继续。
- 保留登录/正常目录分类导航、规格、空购物车、断网后不显示旧可信金额、历史订单/详情返回及历史图片。Node/Vue/WXML 测试不是官方工具通过；官方工具和独立验证尚待宿主执行。

### 初检状态更新

checkout batch3 全链退出 0（21/21），history batch3 全链退出 0（61/61），首页 batch5 退出 0（10/10，含实际原生菜单/规格/加购/导航和 legacy pre-fix 对照）。后端初次 package 的测试阶段 178/178 通过、0 失败/错误/跳过，Boot 打包与隔离 SQL/HTTP 结果待后续记录。文档链接 batch1 的历史宿主材料缺失已按上文说明保留为文字历史引用，batch2 退出 0。

### 初次隔离验收通过（worker）

`bash .sandcastle/environment/issue59-check.sh` 退出 0。证据目录 `.scratch/issue59/sql-NljsyvOT/`；其中 `commands.jsonl` 记录 cwd/命令/退出，`schema-inspection.json` 仅保存实际表/列/主键/索引/约束元数据，`scenario-results.json` 记录 235 个通过断言及关闭状态，`check-result.json` 为 passed=true。先 package 通过（178 测试），再提取 138 个 Boot 库并验证 JDBC、javac 与 probe 均退出 0。

真实 HTTP/auth → prod Service Spring 事务代理 → MyBatis/MySQL 与 Redis，使用独立随机用户/商品/订单 ID；六种菜品/套餐失效场景全部保留身份、数量、口味和历史图片，失效 amount=null/原因明确；拒单零核心写入、旧车不变；他人/匿名减项拒绝。用户明确减项 2→1→移除后只购买 A，服务端可信总额 40.00；有效读后管理员停售 A，再提交拒绝且无订单/购物车损失。订单插入、明细插入、清车三个实际 SQL 写入后分别注入异常，真实事务恢复全部原行；故障关闭后仍可成功购买。历史详情图片、口味、数量、金额及列表归属保留。

检查前实查独立库标记、实际 schema/引擎/JDBC 与 Spring proxy，禁用定时任务/模型训练/评分，地图使用仅合成地址的本地 seam。所有原始/无关 SQL 行与 Redis 快照仅在内存中比较；清理只删除本次自有 ID，恢复原 shop status DUMP 与剩余 TTL；cleanupPassed、springClosed、httpClientClosed 全部 true。未对宿主官方服务自有合成记录做写入或清理；未访问原库或执行外部业务 HTTP/模型/支付调用；依赖下载属于已授权安装。独立验证、官方工具交互和交付仍待宿主，不由此 worker 结果替代。

### Ponytail full 复核与明确结束

已在原预算内读取挂载技能与完整 upstream，从配置 startCommit 审查全部 task diff、未跟踪来源/测试/探针/文档及连接调用方。采用三项：修复范围比较捕获的详情弹窗加号误用购物车条件（首次比较失败保留），补菜品/套餐真实详情弹窗渲染与加购事件回归；移除 home-source 未使用的两个编译依赖（ci 从 25 包减至 5 包，既有 esbuild/Vue compiler 版本不变）；精简探针无关的 #58 预订参数分支。首页局部 install/build/test 退出 0，12/12 通过；原非购物车目录/规格/详情模板段、配置与原 CSS 比较通过。

拒绝全端源码恢复、vendor 重写、新 store/API/计价平台、部分结算、自动清车、扩大管理端/目录修改、hash/baseline/gate。保留严格计价、认证/归属、事务和历史图片；没有未解决的本票实施阻塞。已明确 `stop ponytail` / `normal mode`，随后顺序重跑全部必需命令。最终复验/候选记录见后续；此段不把局部回归当作独立或官方验收。

### 最终 worker 复验：全部通过

2026-10-19 04:51 UTC，原截止时间前完成；仍为 supervisor 1/2，未重置任何迭代/时间配置。以下命令 cwd 均为 `/home/agent/workspace`，非登录 bash，最终命令/退出记录 `.scratch/issue59/final/commands.jsonl`，逐命令日志同目录。

| 必需命令 | 退出 | 实际结果 |
| --- | --- | --- |
| `npm --prefix yuweiju-weixin-miniapp/checkout-source ci --no-audit --no-fund && npm --prefix yuweiju-weixin-miniapp/checkout-source test && npm --prefix yuweiju-weixin-miniapp/checkout-source run build` | 0 | 21 测试通过，精确重建 order 四文件 |
| `npm --prefix yuweiju-weixin-miniapp/history-source ci --no-audit --no-fund && npm --prefix yuweiju-weixin-miniapp/history-source test && npm --prefix yuweiju-weixin-miniapp/history-source run build` | 0 | 61 测试通过，重建八文件且产物无任务外差异 |
| `mvn -B -f yuweiju-backend/pom.xml compile && mvn -B -f yuweiju-backend/pom.xml package` | 0 | compile/package 成功，178 测试，0 失败/错误/跳过 |
| `bash .sandcastle/environment/issue59-check.sh` | 0 | 新目录 `sql-zFl4Gbwo`；锁定首页依赖安装、重建、12 测试通过，再次 package 178 测试通过，138 Boot 库/JDBC 核实，javac/probe 成功，235 真实隔离断言通过 |
| `python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . 'docs/issue#59-executing-report.md' .sandcastle/task-progress.md` | 0 | 无失效本地链接 |
| `git diff --check` | 0 | 无空白错误 |

Ponytail 停止后只补充首页测试的源重建 before hook（复用 checkout 的现有模式，确保 test-before-build 不读旧产物），已审查且上述最终首页 12 项再次通过。无后续业务改动。最终探针 `businessPassed/cleanupPassed/springClosed/httpClientClosed=true`，failure=null；两次隔离运行均通过且完整清理自身数据，官方基线自有数据未变。共享 MySQL/Redis 与官方服务由宿主管理，worker 未停止它们；本 worker 启动的 Spring/HTTP 客户端均已关闭。

暂存限制为本票 28 文件：后端读展示扩展/测试、首页局部来源与精确产物、结算来源/测试与精确产物、用户 API 文档、自包含验收、进度/报告。common/vendor、管理端、认证/事务实现、schema、个人 profile/.env、安装策略均未改动；`.scratch/issue59` 与已有测试产生的 `.scratch/issue5`、`.scratch/issue6` 保留为未提交证据，不进入候选。未执行 push、GitHub 写入、部署或原库操作。

当前结论：worker 完整切片实现、Ponytail 与必需复验通过；候选以本报告所在 Git 任务提交及宿主 result.commits 为准。独立 verifier 与官方工具交互尚待宿主针对最终精确候选运行，逐票人工验收已获豁免；不宣称独立/官方/人工/交付通过。没有未解决的 worker 阻塞。

### 暂存范围复查与最终封存补记

精确暂存 28 文件后，`git diff --cached --check` 发现新跟踪的原生 WXML 来源末尾额外空行（退出 2）；此前普通 diff 不包含未跟踪文件。失败命令/退出保留于 `.scratch/issue59/final/staged-diff-before-fix.json`/log。仅删除源码末尾空行，通过 build 重建对应产物，审查确认无逻辑/事件/布局变化；重新暂存后 cached check 退出 0。

该修正后完整重跑 `bash .sandcastle/environment/issue59-check.sh`，退出 0，最终证据 `.scratch/issue59/sql-mlY93yyi/`：首页锁定安装/重建/12 测试、后端 package/178 测试、javac、真实 SQL/HTTP 235 断言全部通过，cleanup/Spring/HTTP 关闭为 true。其他端与后端业务文件完全未变，保留上表其精确最终检查证据；本报告/progress 封存文字再次审查并复查链接与暂存空白。失败已修复复验，没有绕过检查或追加预算；本次 worker 无剩余实施阻塞，独立/官方验收与交付仍待宿主。


## 宿主精确候选独立验证与官方验收（2026-10-19）

业务候选 `7218e8ec18dae7dd3d8e1ae2e8513c2f92435263`，AFK supervisor 第 1 轮完成，Ponytail 已退出；最终 host/result/resource 记录均正常结束、stopped=true。保留 worktree 的未提交项仅为三个 `.scratch` 测试证据目录，tracked diff 为空。没有将这些证据作为业务源码提交。

统一 `.sandcastle/verify-task.ps1` 针对精确候选创建新独立快照，4 组检查全部 exit0，independentVerification=passed、stopped=true，无认证挂载、无模型。依次运行 checkout ci/test21/build、history ci/test61/build、Maven compile/package178、`issue59-check.sh`（home ci/build/test12、Maven178、真实 SQL/HTTP235 断言），均通过；完整资源与日志 `.scratch/issue59-afk-20261019/independent/task-review-1792386008176672506/`。规范与需求双路只读审查分别为 0 项可执行发现；审查不替代测试。

官方项目端口9459，Tool2.02.2608080 / SDK3.17.4。将候选首页、结算页各4个产物逐字节复制核对后，由官方工具重新编译；隔离副本只调整本机API/socket地址。使用候选真实prod后端、真实认证及独立合成用户会话，SDK访问实际渲染页面、点击既有购物车/减号/结算/支付事件，不通过setData构造金额或绕过业务。真实首页数据已在 launch 前核对。

官方 `official-business.cjs` 退出0，5项全部通过（`official/business-results.json`）：

1. 菜品停售：A+B可读，B原因“商品已停售”、amount=null、实际行无价格、总额“待恢复”；结算点击留在首页、无SQL变更；主动减项2→1→移除后，仅A结算并创建可信40元订单，只有本人车清空。
2. 菜品删除：相同全链通过，原因“商品已删除”。
3. 套餐停售：相同全链通过，真实既有管理员状态API触发停售。
4. 套餐删除：相同全链通过，删除只操作本票自有合成目录ID。
5. 读后再停售：结算真实读取A可购买后点击一次“去支付”，网关在转发实际submit前先调用真实管理员停售API，随后转发原请求；严格服务端拒单，实际页面仍在结算页、无新订单且旧车逐行不变。没有替换订单响应/Mapper/事务。该场景模拟刷新与提交之间的实际状态变化。

每次实际提交后先核对持久结果，不因Page/截图响应重复下单；四次合法提交分别按各独立场景执行。所有场景保留他人购物车。场景之间仅重建本票具名用户/商品/购物车夹具，不修改原库或历史订单。身份/密钥仅保留任务本地会话，日志不输出令牌。

官方 finally 恢复本项目SDK会话，宿主触发服务正常finally清理，结果 originalSqlRowsPreserved/redisPreserved/springClosed 全true；网关停止，MySQL/Redis停止，本票运行容器0，官方项目关闭，卷和证据保留。记录 `.scratch/issue59-afk-20261019/cleanup-result.json`。未执行原库、真实付款、Release或正式部署。

用户已免除逐票人工验收；必需的Agent独立/真实业务/官方验收均已满足。本交付追加仅为报告文字，业务/测试/构建/探针代码与已验证候选相同，验证证据按其精确候选复用。PR/合并/关闭的最终远端状态由宿主交付记录追加，不在本节预写成功。
