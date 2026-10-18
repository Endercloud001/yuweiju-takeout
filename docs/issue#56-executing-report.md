# #56/F2 套餐编辑保留菜品份数

本票从已交付 #55 的 main `81cd5f742d03be4422691270bdc68b395c5fb50a` 开始，只处理 F2。候选为包含本报告的正常本地任务提交；提交后完整 SHA、文件范围和 Git 状态记录在 `.scratch/issue56/candidate.json`。worker 检查与 Ponytail 已完成；宿主独立验证尚未执行。

## 范围与语义

管理端 `/setmeal/add?id=…` 使用现有 `src/views/setmeal/add.vue`。读取到的合法 `copies` 随选中菜品保存，修改名称或图片后使用既有 PUT 请求原样提交；菜品列表 API 的 `DishItem` 本身没有 copies，不能用它覆盖已选项的份数。新加入的菜品默认 1，不增加份数编辑 UI。

“保留”指菜品一直存在于当前选中集合中。抽屉重开、确认、切换分类、移除其他菜品都不应改变其份数。“移除后重新选择”指从当前抽屉集合删除后再勾选（即使尚未关闭抽屉），作为新选择默认 1；确认移除后重开再选也相同。取消抽屉则舍弃临时移除，已确认集合仍保留原份数。这沿用本次明确授权的新选择语义，不从历史详情找回已移除菜品的旧份数。

实施步骤：先挂载真实页面记录修复前失败，再仅调整选中状态/份数提交和必要表格同步；核对既有 API/Service 份数证据；完成规定命令；Ponytail 复核全部任务差异，退出后重跑检查并提交。回退可通过正常 revert 本票代码提交完成，无数据迁移或批量修复。

## 三端与调用链

- 管理端：`getSetmealById` → `fetchDetail` → `selectedDishes` → 抽屉 `dishPickerSelected` → `submit` → `editSetmeal`。现有 Cookie token、请求层、路由守卫、表单校验、保存中/加载中/错误提示及所有模板样式保持原实现。
- 后端：`AdminSetmealController.update` 读取管理员身份后委派 `SetmealApplicationServiceImpl.update`，现有 `@Transactional` 边界保留。`SetmealDishDTO.copies` 为 Integer；`saveSetmealDishes` 将其直接写到 `SetmealDish.copies`，`SetmealDishServiceImpl`/`SetmealDishMapper` 对当前套餐明细操作，实体映射 `setmeal_dish.copies`。详情装配器保留原明细。无后端生产代码、接口或 schema 变更。
- 小程序：`common/vendor.js` 的 `querySetmealDishById` 调用 `/user/setmeal/dish/{id}`，首页 `openDetailHandle` 接收 `dishMealData`，`pages/index/index.wxml` 显示 `item.copies`。后端 `userDishes`/`SetmealAssembler.toUserSetmealDishVO` 已返回存储份数，消费者无需修改。本票没有小程序源码/产物变更，无需官方微信能力。
- 历史订单：本票不修改订单链路。套餐保存 Service 的依赖和写入限于套餐及当前套餐关联明细，不调用历史订单写入；没有数据迁移或订单重写。这里只报告源码范围，不将 mock 测试宣称为真实 SQL 或事务证明。

## 检查与证据

命令工作目录均为 `/home/agent/workspace`，使用非登录 shell。日志和每次命令/目录/退出记录位于 `.scratch/issue56/`；重复执行记录为 check batch，不是监督器 iteration。已确认 Java `/opt/java/openjdk/bin/java`，Maven `/usr/share/maven/bin/mvn`。

- 必跑管理端：`npm --prefix yuweiju-web-vue/yuweiju-admin ci --no-audit --no-fund`，随后 `run lint`、`run typecheck`、`test`、`run build`。
- 必跑后端：`mvn -B -f yuweiju-backend/pom.xml compile`，随后 `mvn -B -f yuweiju-backend/pom.xml test`。
- 新页面测试：真实 Vue Test Utils mount、Element Plus 表单/表格/对话框、DOM 输入/点击、按实际签名的 API mocks；不访问组件私有方法或使用 regex 替代页面行为。
- 复用后端精确源码证据：[Issue8Probe.java](../.sandcastle/environment/Issue8Probe.java#L212) 第 212–213 行通过现有管理端 PUT 提交 copies=3，随后从用户套餐详情 API 断言 copies=3；第 75–76、210 行还覆盖新建 copies=2 的读取。其成功执行来源是既有 [#8 实施报告“精确提交独立验证”](issue%238-executing-report.md#精确提交独立验证) 所记录的 158 断言通过。当前保存 Service 和该探针最后变更同为 `0af28af`（#8），DTO 自导入后未变；本票无后端生产改动。该历史证据明确覆盖合法 API/Service 保存的份数传递，因此不重复新增 backend 普通测试。本轮没有重跑该 SQL 探针，也不把历史独立结果记为本票宿主独立验证通过。
- 准备阶段首次启动检查 runner 使用了不存在的 `python`，退出 127，Maven 尚未启动；失败命令已保存，改用已确认的 `/usr/bin/python3` 在同一授权 iteration 内继续。不会隐藏该失败。

实际 check batches：

| 批次 | 检查与结果 |
| --- | --- |
| install1 | 指定 `npm ci --no-audit --no-fund`，退出 0，无依赖或锁文件改动。 |
| regression1（修复前） | `npm --prefix yuweiju-web-vue/yuweiju-admin test -- src/views/setmeal/add.test.ts`，退出 1，6/6 预期失败：名称/图片修改提交 1/1 而非 3/2；重开/取消抽屉丢勾选；右侧移除未取消表格勾选。日志保留断言栈和实际份数，完整合成请求体已脱敏省略。 |
| backend-compile1-corrected | 指定 Maven compile，退出 0；首次依赖准备及编译共约 4 分半。compile 不算业务测试。 |
| admin2 | 指定 lint、typecheck、test、build 均退出 0；Vitest 8 文件 43/43，包括新增 8 个真实页面回归。Vite 构建 41.97 秒，仅原有 chunk-size 提示。 |
| backend2（初检） | 指定 compile、test 均退出 0；172 项、零失败/错误/跳过。此时包含后来在 Ponytail 移除的 1 个重复测试，最终数量另记。 |
| admin3（退出 Ponytail 后） | 指定 lint、typecheck、test、build 全部退出 0；8 文件 43/43（本票 8/8）；Vite 构建 47.96 秒。 |
| backend3（退出 Ponytail 后） | 指定 compile、test 全部退出 0；最终 171 项，零失败/错误/跳过。已排除重复测试的源文件、编译 class 和旧报告。 |

修复后页面实际结果：名称单独修改和默认图片库单独选图都提交原 3/2；重复打开/确认维持 3/2；右侧删除其他菜品、切换分类并添加新菜品提交 3/1；同抽屉移除再勾选和确认移除后重开再勾选均为新 1，同时保留其他菜品原 2；取消临时移除后恢复原 3/2。同分类表格勾选变化也保留持续选中项的 3，选项没有 copies 字段不会覆盖它。原合成详情对象的 copies 没有被页面修改。

## Ponytail 全量复核

初检通过后读取挂载的 `yuweiju-ponytail/SKILL.md` 及 `references/ponytail-upstream.md`，按 full 级别复核自 startCommit 的所有任务差异、未跟踪测试/报告/检查辅助文件及调用方：页面加载/提交、抽屉勾选/移除/取消/确认、类型/API/路由/请求层、真实页面与图片回归、后端 DTO/Controller/Service/Mapper/装配器及小程序消费者。无先前任务提交或未审阅的其他任务改动。

- 采用：复用上面精确的 #8 API 份数行为证据，移除本轮重复编写的 `SetmealCopiesTest`；初检日志保留，不以 mock 持久化回读增加证明范围。移除该自有生成 class 和旧测试报告，防止 Maven 缓存把已删除测试计入最终结果。
- 保留：选中项直接携带 copies、仅新选择默认 1；表格清空和重建放在已有同步保护内，统一解决重开丢选和右侧移除未取消勾选。真实失败回归证明两部分均为 F2 必需。
- 拒绝：新通用选择 composable/状态框架没有复用需求；换成表格/表单 stub 或私有方法测试会失去实际交互证据；份数编辑器、后端通用校验和数据修复超出票范围。没有其他值得采用的精简。校验、认证、异常处理、事务和历史行为均保留。

`stop ponytail / normal mode`。退出后重跑管理端 lint/typecheck/test/build 与后端 compile/test；npm ci 已通过且依赖/锁文件未改，沿用 install1。

## 候选范围与待宿主事项

最终任务提交只包含 `add.vue`、同目录 `add.test.ts`、本报告及追加的 `.sandcastle/task-progress.md`。所有实际命令/工作目录/退出状态及失败修正见 `.scratch/issue56/*.json` 和 `checks.md`，日志同目录保留；未提交的 scratch 仅为检查证据，没有未完成的源码改动。最终源码在通过检查后未再修改，无当前阻碍。

宿主对精确候选的独立 20 分钟验证、发布/交付仍由宿主执行，worker 不标记通过。逐票人工验收已由用户明确豁免，不再请求确认。没有原库访问、数据库服务、正式发布、GitHub 写入或凭据读取；没有新增 hash/contract/baseline/gate，也未调整迭代或预算。

本票 UI 验证为真实挂载页面的 JSDOM/Element Plus 行为，未另做浏览器截图或真实登录/匿名 HTTP 检查。请求层与路由认证源码未改；已有图片上传失败测试仍通过。未调用原库或现存应用服务；本轮不新增 SQL/官方微信能力要求。未启动持久任务服务，测试进程结束后无需保留应用/数据服务。
