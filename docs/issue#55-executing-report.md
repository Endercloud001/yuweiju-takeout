# Issue #55 执行报告

候选起点：`349628aba8cdee17dc572f0ef6b48a5a83198961`。源码候选为 `7cb7acad5c9bf6194461eeeddf814cb7a5df3e82`；Worker 实现、Ponytail、独立验证及官方四个必需业务场景均已通过。交付阶段仅更新本报告，源码与该候选一致。仅处理 Endercloud001/yuweiju-takeout #55。

## 范围、行为与兼容

历史列表和订单详情的可编辑 `history-source/src/history.js`、`details.js` 删除前置独立清车，仅发送 `POST /user/order/repetition/{id}`。业务 code 非 1 时提示错误且不跳转；成功沿现有导航返回首页。保留 disposed 检查、加载/销毁生命周期、页面模板、样式、历史图片和认证请求头。真实编译器重新生成两页，生成差异仅 JS 中对应清车调用。

后端继续复用 `OrdersApplicationServiceImpl.repetition` 的既有 `@Transactional`、`OrdersMapper.findOwned`、用户范围内清车和明细重建，保持替换而非合并。后端生产代码、接口字段/code、schema、原数据均无修改。管理端没有本入口或接口变化，无需修改；无迁移。回退方式为普通审查后 revert 本票提交，无数据迁移。

超时只表示客户端未得到明确结果，不能推出服务端未提交，也不自动重试变更请求。现有首页 `onShow → init → getTableOrderDishListes → GET /user/shoppingCart/list → initdishListMut` 在用户下次返回首页时读取实际购物车；保持这一模式。页面测试同时模拟超时前未提交、提交后丢失响应，两种结果均不错误导航，下次首页 onShow 按实际结果刷新；不承诺 exactly-once。

## 方案与隔离

先读 mounted issue/spec/authorization、root/miniapp/backend AGENTS、workflow、miniapp/backend standards、domain/GLOSSARY/ADRs、实际 API/调用链和既有探针副作用。历史 AFK 记录保留，不当作本票证据。非登录 shell 确认 Java/Maven 路径；本票所有新产物位于 `/home/agent/workspace`，日志/证据在 `.scratch/issue55`。既有 build 测试还会产生 `.scratch/issue6/source-edit-*`，不提交这些临时产物。

新增 `Issue55Probe.java` 和 `issue55-check.sh` 是独立可重复检查入口，需 host 已归属的内部 `mysql:3306`/`redis:6379` 服务。脚本自身等待 `mvn package` 成功后才从 Boot jar 提取真实依赖并编译/运行探针，不依赖 worker 缓存或此前数据。使用独立 `application-afk.yml` 替换配置搜索，并强制内部 `sandcastle_fixture`/Redis；禁用全部定时调度、风险评分、模型训练，不调用登录/地图/天气/OSS/外部模型，不使用原库或模型文件。

写入前核对 `.sandcastle/environment/schema.sql`：涉及表均为 InnoDB、主键 id；orders 的 user/address/order_time/amount 必填，order_detail/shopping_cart 的 amount 必填、number 默认 1；dish/setmeal 的 category/name/price 必填，名称唯一；address_book 的 phone 必填、is_default 默认 0。再从实际 information_schema 读取主键/所有列的类型、NOT NULL、默认值及引擎，保存 schema 元数据（无业务行）。先验证 DATABASE 和 900001 的 user/employee/category 合成标记，然后分配本次独有 ID、核对无占用、自行播种两个独立用户、旧车、目标菜品/套餐、本人/他人历史订单。快照按实际主键排序。

真实 HTTP 经过现有 JWT 拦截器/Controller/Spring 事务代理/MyBatis/MySQL。夹具 token 仅在内存，未记录。测试专用 MyBatis interceptor 在真实 DELETE 后或 batch INSERT 真正 flush 到数据库后抛异常；在事务内部确认清空/重建已发生，HTTP 返回失败后比较完整 SQL 快照验证旧车回滚。finally 仅删除本次 ID/用户范围内行，并比较全部涉及表与播种前快照，保留其他合成数据。Redis仅 ping/认证黑名单读取，不写缓存。SQL 回滚证明不扩展为 Redis/文件/HTTP 副作用回滚。

## Worker 检查批次

首轮与 Ponytail 后最终完整批次均已通过。命令均从 `/home/agent/workspace` 使用非登录 bash 执行，日志路径见 `.scratch/issue55`，重复执行记为 check batch，不计作监督器新 iteration。

- 准备批次：`npm --prefix yuweiju-weixin-miniapp/history-source ci --no-audit --no-fund` exit 0。
- 修复前回归：`node --test --test-concurrency=1 --test-name-pattern=issue55 yuweiju-weixin-miniapp/history-source/tests/template.test.cjs` exit 1；24 项新增场景全部失败，实际请求先出现 DELETE clean。证据 `regression-before-fix.log` 保留。
- 页面批次 1：真实 `npm ... run build`、`npm ... test` exit 0，61/61 通过。新增24项覆盖两页×源码渲染按钮/生成 WXML 的实际 `__e` 事件×拒绝/成功/超时未提交/超时已提交/销毁后成功与拒绝。既有页面测试不再期待 DELETE；现有模板/图片/其他页面构建保护检查一并执行。
- `mvn -B -f yuweiju-backend/pom.xml compile` exit 0；编译只是准备，不替代测试。
- 首轮完整 `initial-check-batch`：依次执行 `npm --prefix yuweiju-weixin-miniapp/history-source ci --no-audit --no-fund`、`npm --prefix yuweiju-weixin-miniapp/history-source test`（61/61）、`npm --prefix yuweiju-weixin-miniapp/history-source run build`、`mvn -B -f yuweiju-backend/pom.xml test`（171/171）、`mvn -B -f yuweiju-backend/pom.xml package`（171/171）、`bash .sandcastle/environment/issue55-check.sh`，六条命令全部 exit 0。逐条 cwd/起止/退出/日志见 `.scratch/issue55/initial-check-batch.json`。
- SQL 首轮证据 `.scratch/issue55/sql-C6pkREsb/`：package/javac/probe exit 0，Boot 依赖 138 项，59 个明确断言通过；`businessPassed/cleanupPassed/springClosed/httpClientClosed` 均为 true。真实清车与重建 SQL 已发生后异常回滚，完整行快照一致；匿名、missing、foreign、另一身份拒绝均无 SQL 改动。原合成行保留，本票行已清理。独立 script 运行不依赖外部 backend-classpath.txt 或旧 probe 数据。
- Ponytail 后 `final-check-batch` 按相同六条命令/顺序全部 exit 0：页面 61/61，Maven test/package 各 171/171，无跳过。完整命令/cwd/时间/退出见 `.scratch/issue55/final-check-batch.json`，逐条日志 `final-check-batch-{1..6}.log`。
- 最终 SQL `.scratch/issue55/sql-YpeAQlaK/` 又以新的独有夹具通过 59 项断言，四个 business/cleanup/Spring/HttpClient 标志均 true、failure=null。与首轮不同运行目录，重新 package、提取 138 项 Boot 库、编译探针，不依赖首轮数据；再次 scoped cleanup 后完整原行快照一致。证明脚本可重复、自行播种和关闭资源。SQL JSON 只含断言/状态、schema JSON 只含列与主键信息，不记业务行或 HTTP 载荷。
- 修复前预期失败日志保留；两轮正式检查没有失败命令。`bash -n .sandcastle/environment/issue55-check.sh`、`git diff --check` 通过。只显式暂存本票 10 个文件，scratch 日志/编译产物/构建测试临时目录不提交。最终提交后 SHA 和剩余状态另存 `.scratch/issue55/candidate.json`；候选 SHA 以本报告所属 Git 提交为准。

## Host 独立与官方验证（已完成）

宿主统一 verifier 对精确候选 `7cb7acad5c9bf6194461eeeddf814cb7a5df3e82` 新建快照，在无认证挂载、无模型调用的独立容器中执行以下命令（项目根、非 root、C.UTF-8）：

```sh
npm --prefix yuweiju-weixin-miniapp/history-source ci --no-audit --no-fund && npm --prefix yuweiju-weixin-miniapp/history-source test && npm --prefix yuweiju-weixin-miniapp/history-source run build
mvn -B -f yuweiju-backend/pom.xml compile && mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue55-check.sh
```

三组均 exit 0，页面 61 项、后端 171 项和真实 SQL 59 条断言通过，`independentVerification=passed`、`stopped=true`。证据保存在项目 `.scratch/issue55-afk-20261018-resume3/independent/task-review-1792313745970126068/`，不包含在提交中。交付阶段只修改本报告，因此复用该精确源码验证，不把文档版本变化算作重新跑过业务测试。

官方开发者工具 `2.02.2608080` / SDK `3.17.4`，本票隔离项目连接候选真实 Spring/MySQL/Redis；使用独立合成身份 A/B，生产认证和归属判断继续生效。四个场景从真实 WXML 按钮触发：

| 场景 | 已核对结果 |
| --- | --- |
| 历史列表拒绝 | 一次 repetition POST、无 clean；A/B 购物车不变，失败 toast，留在历史页 |
| 历史列表成功 | 仅目标菜品 2 份/套餐 1 份，B 购物车不变，返回首页 |
| 订单详情拒绝 | 一次 repetition POST、无 clean；A/B 购物车不变，失败 toast，留在详情页 |
| 订单详情成功 | 仅目标菜品 2 份/套餐 1 份，B 购物车不变，返回首页 |

结果 `official-resume1/business-results-resume2.json` 为 passed=true，四场景全部通过。三个截图调用发生 SDK 超时，单独记录，不能写成截图成功；每次断开旧连接再重连，不重发已经执行的变更请求。详情成功取得有效截图，首页购物车 3 件/45 元及目标菜品 2 份与 SQL 断言一致。网络超时/销毁保护引用候选实际页面自动回归，未声称在官方四场景额外重做。

人工逐票验收由用户明确豁免；上述验证由 Agent 执行，不声称真实微信双账号/真实支付验收。

## 准备失败、恢复及资源

GitHub 连接和本地 clone 的早期失败、首次官方当前页元数据为空、首次 Windows→WSL 后端连接拒绝、SDK 截图超时均保留原证据。未将失败标为通过。官方后端仅 internal 网络时 Windows 连接失败，同时接入已授权本票出口网后恢复；MySQL/Redis 继续只在 internal 网，端口只发布至 127.0.0.1。Windows 对真实受保护 API 的正确身份通过及匿名拒绝均已核对。

旧 shutdown hook 清理失败后按会话专属合成 ID 做事务化定向清理，零残留检查通过。本次正常结束先清理 fixture、对比全部原行，再关闭 Spring；`official-cleanup.json` 为 `originalRowsPreserved=true`、`springClosed=true`，后端退出 0。Windows 网关按 PID/命令行确认归属后停止；MySQL/Redis 均已停止。卷、日志、候选和截图保留，无原库写入、Release 或部署。

复盘建议：服务就绪必须包含 Windows 侧实际受保护 API 请求，WSL 本地探针明确绕过代理，夹具清理安排在 Spring 关闭之前。本票恢复验证环境已采用这些必要修正；不扩展为新平台或安全门槛。

## 交付范围与状态

AFK 实际第 1 轮完成，未重启模型，未增加最多 2 轮/60 分钟编码及 20 分钟独立验证预算。候选已推送到 `codex/issue55-cart-preservation-20261018`，远端 SHA 已核对。交付工作树基于最新 main `349628aba8cdee17dc572f0ef6b48a5a83198961` 快进到该候选；主工作区的 AGENTS.md 修改、PLANS.md 删除、local-development.md 修改及未跟踪文件均保留。

本报告的后续文档提交仅补齐实际验证状态；远端 PR/head/merge/issue 最终结果以宿主交付记录及 GitHub 状态为准。下票必须从实际已合并的 main 开始。
