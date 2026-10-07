# Issue 19：经营查询职责恢复方案

依据 issue #19、当前任务授权、workflow、三端 standards、GLOSSARY、accepted ADR 0001/0002、restore-spec B3 / US19–21 与 grilling Q5/Q6。前置 #2 已关闭。历史 JSON/截图已清理，不恢复，不连接原系统。初始 git status 为空。

## 消费者与兼容语义

管理端实际路由 `/dashboard` → api/modules/workspace.ts 的 GET `/admin/workspace/businessData`、`overviewOrders`、`overviewDishes`、`overviewSetmeals`，shop.ts GET `/admin/shop/status` 与 PUT `/admin/shop/{status}`。`/statistics` → dashboard.ts GET `/admin/report/turnoverStatistics`、`userStatistics`、`ordersStatistics`、`top10`（begin/end），GET `/admin/report/export`（携带日期，但既有后端忽略参数，固定昨日结束的近30天）。保持这一导出规则。

小程序 common/vendor.js 的 getShopInfo/getShopStatus → GET `/user/shop/info`、`/user/shop/status`，首页 `/pages/index/index` 使用信息/营业状态；authentication、code=1、地址与状态字段不变。管理端 token/VITE_API_BASE/code=1、CSV统计字符串（页面现有转换）、Excel文件名/MIME/单元格、外观不变。两前端无具体必要源代码变更；原生小程序交互无额外可重建源码/IDE证据，明确未验证。

时间保持 systemDefault，包含起止，日末为下一日零点减1ms；有效订单仅 COMPLETED；工作台今日零点至调用当前时刻，订单概览仍全历史，delivered仍 CONFIRMED。金额仅完成订单 amount SUM，报表客单价 BigDecimal HALF_UP 两位，工作台 double除法不舍入。TOP10继续按销量降序、不新增并列排序规则。无数据零计数/金额/比率。

## 范围与步骤

1. Report/Workspace service 以同一个实际具名 OrdersMapper 聚合复用计数/完成金额，UserMapper 日期计数，Dish/SetmealMapper 状态计数，OrdersMapper 全历史状态计数；复杂聚合与TOP10移至绑定参数 XML。内部 VO 不改变公开字段。
2. Service 编排默认导出周期、汇总与每日数据；Controller 只负责 HTTP/POI Excel排版与流，异常保留 cause、返回友好错误。Service 日期规则阻止非法直接调用，HTTP既有非法日期业务响应不变。
3. 店铺 prod Redis 缺 key仍默认营业，非0状态仍归一为1；核心GET/SET故障不再吞掉，不伪造成功。dev恒营业/no-op保留，数据库事务不适用于单Redis操作。不调整运行profile/auth/supervisor，不新增SQL店铺状态。
4. 普通测试覆盖空/精度/日期/SQL与缓存故障/导出周期。隔离 Issue19Probe 在原事务检查之后、READY之前建立专属数据，验证真实Mapper/Spring/HTTP/Excel与边界，再finally只删除自身记录；浏览器正常密码登录检查dashboard/statistics实际业务请求、导出、日期拒绝、权限和shop。保留既有探针/浏览器检查与错误可见性。

## 三端影响与验证命令

后端内部职责及核心故障表达修复；管理端协议/视觉无需修改；小程序相同正常状态响应、缓存故障返回失败，原生交互未验证。API/schema、凭据与认证安全措施不变。

Sandbox命令（根目录）：

```sh
mvn -B -f yuweiju-backend/pom.xml package
cd yuweiju-web-vue/yuweiju-admin && npm ci && npm run lint && npm run typecheck && npm run test && npm run build
```

独立探针编译绑定 build-check.sh/runtime-check.sh（包含 EnvironmentProbe 与 Issue19Probe），浏览器绑定 browser-check.cjs。宿主使用现有隔离 compose：

```sh
docker compose -f .sandcastle/environment/compose.yml up -d --wait
python3 .sandcastle/environment/verify.py --commit TASK_COMMIT
```

TASK_COMMIT 在交付输出绑定实际任务提交。sandbox不假定Docker socket；宿主尚未运行不得称真实SQL/Redis/浏览器验收通过。最多两次配置迭代，不自动无限重试。命令/退出状态/未完成项记录 .sandcastle/task-progress.md。

## 回退与提交

本轮续办先核对已提交实现（e5cd53e），保留全部已有成果。补充普通 Controller 测试：使用真实 Excel 模板核对 Service 返回数据的数字单元格、文件协议及核心查询失败的友好 HTTP500；补齐独立探针的汇总单元格与 HTTP 工作台精度断言。仍使用上述 package 与前端完整命令，本轮属于配置允许的第二次任务迭代；宿主独立运行结果待宿主提供。

只stage本票文件，diff检查范围后本地task commit，不push/GitHub写入/merge/release。代码回退可git revert本票提交；不改变schema与原数据。隔离数据采用独有ID并finally清理，不删既有fixture，不清表/清缓存。店铺Redis探针仅对隔离库的实际店铺键操作，finally恢复原值（原无键则删除本次创建的键）；SQL回滚不代表Redis回滚。未决业务冲突或未解决检查失败则停止并报告。不恢复PLANS、不增hash/冻结contract/baseline/gate。


## 本轮实现与验收状态

调用链：AdminReportController → ReportApplicationServiceImpl → OrdersMapper.aggregateBusinessByOrderTimeRange（Report/Workspace共用 actual SQL聚合）/UserMapper.countCreatedInRange/countCreatedThrough/OrderDetailMapper.top10；AdminWorkspaceController → WorkspaceApplicationServiceImpl → 上述聚合、OrdersMapper.countAllOrders/countByStatus、DishMapper/SetmealMapper.countByStatus。Service无Wrapper/业务SQL。export → prepareExport返回固定30日汇总和每日数据 → Controller POI排版/HTTP流。

AdminShopController 与 UserShopController → ShopStatusService：prod单RedisGET/SET，缺失键和非零归一规则不变，故障抛友好BusinessException保留cause；dev恒营业/no-op无diff。店铺没有SQL持久化，不虚构Mapper/SQL事务；Controller信息字段映射职责合理保留。读聚合不增加写事务。导出失败仍HTTP500/plain text，移除原异常详情泄露，服务失败不产生伪造Excel指标。

协议证据：管理端 src/api/modules/{workspace,dashboard,shop}.ts 保留上述方法/路径；src/api/http.ts token、VITE_API_BASE、code=1与blob响应；statistics/index.vue splitList处理原CSV串、handleExport下载。小程序common/vendor.js getShopInfo/getShopStatus的GET请求，pages/index/index.wxml shopStatus===1显示营业中/否则休息中，均无需修改。新增browser输出actualResponses记录实际路由请求方法、日期query、业务code和数据，不输出token。

验证结果：前轮依赖下载TLS失败已记录于task-progress，未冒充测试成功。本轮续办在配置允许的第二次任务迭代中执行完整验收，管理端npm ci/lint/typecheck/test/build整链exit0，Vitest2例通过。后端新增Controller测试首次预期遗漏既有charset，修正测试预期后完整package exit0：31 tests，0 failures/errors/skipped，包含统计7例、缓存3例、Excel协议/失败2例。实际Java21运行，保留POM现有release17设置。独立javac编译EnvironmentProbe/Issue19Probe/StorageProbe/TrainingProbe exit0，node --check、bash -n、XML解析与git diff --check均exit0。探针编译与语法检查不证明运行时业务通过。

隔离探针在既有事务与算法检查后运行，专属919xxx订单/明细/用户finally清理，Redis原key值finally恢复。浏览器检查消费清理后的空统计与0值Excel；非空10元/3单/3.33与工作台10/3及日期边界由之前真实SQL/HTTP/Excel探针检查。用户info使用原隔离fixture900001的短期测试JWT，不宣称微信/原生小程序正常登录；该文件仅runtime下0600、浏览器finally删除、runtime cleanup兜底，未改auth/profile/supervisor配置。保留原密码登录/WebSocket/客服/算法/事务探针。

待宿主验收：host verify.py的隔离MySQL/Redis/Spring/HTTP/浏览器未运行（sandbox无Docker命令/socket）；原生小程序交互未验证，无额外源码可用于本轮原生恢复。交付本地任务提交用于宿主独立验证，本地完成不表示宿主已通过。宿主使用最终任务提交执行上述verify.py命令，不访问原系统。
