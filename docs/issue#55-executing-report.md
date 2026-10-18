# Issue #55 执行报告

候选起点：`349628aba8cdee17dc572f0ef6b48a5a83198961`。本报告对应本票候选提交（由宿主通过 `git log -1 --format=%H -- docs/issue#55-executing-report.md` 取得最终 SHA）；Worker 实现、Ponytail 和最终检查全部通过；本报告随最终候选一起提交，等待 host 独立及官方验证与交付。仅处理 Endercloud001/yuweiju-takeout #55。

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

## Host 官方验证交接（未执行，必须绑定精确候选）

宿主保留的 Tool.getInfo 版本 2.02.2608080、SDK 3.17.4、`pages/index/index` 及截图仅为启动就绪，不代表本候选业务验收通过。Worker 不运行官方 SDK，不写 GitHub、不 push/merge/close。人类逐票验收已由用户豁免；独立检查与官方业务场景由 host 完成后再交付。

宿主先将精确候选的生成页更新到已受信任的本票项目并真实重新编译，保留 private/project 安全配置。只可使用具名隔离后端及合成数据。独立 verifier 执行相同脚本会自行生成/清理自己的数据；官方场景应另外建立自己的可保留合成夹具，不能依赖 worker 的已清理行。

官方夹具：两名不同主键的合成用户 A/B；A 原车仅 oldDish×3；A 已完成订单仅 targetDish×2（flavor mild、历史图）和 targetSetmeal×1，B 也有自己订单/车，商品均可售。设置实际用户 A 的 authentication（勿记录 token）。精确播种 SQL 见 `Issue55Probe.seed/seedCart`：选未占用 base，owner=base、other=base+1、oldDish=base+2、targetDish=base+3、targetSetmeal=base+4、ownOrder=base+5、foreignOrder=base+7，先复用探针的 DATABASE/900001/无占用核查，再以本次 UUID marker 参数绑定写入；仅使用这次 ID，finally 复用同范围 cleanup SQL。每个入口的每个场景前都将 A 车重置为 oldDish×3、B 车为 oldDish×4；成功应仅有 targetDish×2/targetSetmeal×1，两车和历史按 id 排序核对。记录断言名和数量，不记载荷。失败场景在列表/详情完成正常加载后，只在具名隔离库删除 A 的本次测试订单主记录（并由 fixture finally 清理其明细），再点仍显示的订单按钮，以真实 missing-order 拒绝验证 UI；或将已渲染测试订单所有者改为 B，并在 finally 恢复本票夹具归属。不得对原库操作。真实 SQL rollback 已由独立脚本专门验证，官方失败场景不需要安装生产故障开关。

实际路由和选择器：

| 场景 | 路由/事件 | 必须核对 |
| --- | --- | --- |
| 历史入口成功/失败 | `pages/historyOrder/historyOrder`，当前 swiper pane `.order_lists .againBtn button.new_btn` 中正文“再来一单”，真实 tap | 仅 POST `/user/order/repetition/{A-order}`，没有 DELETE `/user/shoppingCart/clean`；失败 toast、留在当前页、A/B 原车不变；成功回到 `pages/index/index`、实际车只有两条目标明细、B 车不变 |
| 详情入口成功/失败 | `pages/details/index?orderId={A-order}`，`.againBtn button.new_btn` 中正文“再来一单”（按正文选按钮，勿直接 call Vue 方法） | 同样请求/提示/不导航/替换断言；成功 redirect 到 `pages/index/index` |
| 超时不确定结果 | 两个入口各以本票传输夹具丢弃 POST 响应（可在服务端成功后丢弃），不修改生产代码 | 仅一次变更请求、失败提示不声称未提交/已回滚；随后真实导航首页，GET `/user/shoppingCart/list` 与隔离 SQL 实际结果相符，不自动重发 POST |
| 生命周期 | 请求未完成时离开/销毁两页，再放行本票响应 | 不出现迟到 toast/错误导航，详情计时器照旧清理 |

历史页有三个 swiper pane，按当前 tab 选择可见按钮；按钮绑定到实际渲染行 ID。详情使用相同 `.againBtn` 容器；按“再来一单”正文区分退款/取消等相邻按钮。列表 `GET /user/order/historyOrders`，详情 `GET /user/order/orderDetail/{id}`，购物车 `GET /user/shoppingCart/list`，均保留 `authentication`。匿名请求和另一个真实合成主键的归属拒绝另由 HTTP/SQL 探针验证；API 替身只证明页面联动，不能代替事务或官方渲染。

## 收尾与限制

Ponytail full 已在首轮全部检查通过后读取 mounted SKILL.md 和 upstream 5.1.0，复核全部起点差异、两页真实调用/编译器/旧 API/store、后端 Controller/归属 Mapper/事务 Service、配置、旧探针副作用及新增 probe/script/report/progress（含未跟踪任务文件）。采用：合并重复 runtime import；更正页面 mock 注释及 SQL GET 断言名，准确区分页面 IO 夹具、真实 API/SQL、实际导航。生产修复保持最小删除。

拒绝：新增通用 probe/请求框架、生产事务改写、自动重试/幂等平台、全库清理/迁移、重构相邻业务、减少归属/真实写入/清理断言，均不符合本票范围或会削弱所需证据。无其他值得实施的精简。已明确 `stop ponytail / normal mode`，随后重跑完整必需检查且全部通过。最终报告结果更新已对照 JSON 复核；该更新仅记录证据，无后续源码修改。首轮 probe 关闭状态已实测，不表示停止 host 管理的 MySQL/Redis；两项内部服务由 host 按既有生命周期管理，worker 不清卷。共享60分钟/最多2次监督器迭代、closing2分钟以内；实际截止 2026-10-18T09:30:39.274Z，host20分钟独立验证单列。不延长预算、不自动重启。`/afk-evidence/closing.json` 在工作步骤间检查，当前尚未出现。Host 独立 exact-commit 检查、官方两入口正常/失败/截图均未执行，不得标为通过。无迁移、无正式发布、无原业务数据操作。
