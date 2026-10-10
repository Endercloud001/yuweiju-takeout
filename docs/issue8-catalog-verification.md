# Issue #8 商品目录职责与验收记录

## 授权、来源与标准

当前 baseline main e6d62a7379b505713f182caafa45e14ed3c65fcb，已含 #7 合并与 AFK 工具。issue #8 OPEN、无评论；原生 blocker #2 CLOSED。维护者已授权本轮实施/正常本地 task commit，最多 2 个 supervisor iteration、总编码 60 分钟（安装/检查/提交都包含），截止 2026-10-10T06:04:35.862Z；host 独立验证另有 20 分钟，不由本 agent 消耗或追加。无额外 agent、模块、依赖、认证/schema 或发布变更。

已读取 root 与三端 AGENTS、docs/agents/workflow.md、domain.md、GLOSSARY、ADR 0001/0002、docs/standards/backend.md/admin.md/miniapp.md 和 task-input 的 issue/阻塞/标准决定副本。适用规范：常规 Controller/Service/Mapper、全部目录业务条件具名 Mapper、Service 规则及代理事务、构造器注入、异常 cause、接口/分页/鉴权/schema 兼容、隔离失败测试。ADR 0002 不要求目录详情冻结现图；订单历史图及其他业务不在本票。方案见 `yuweiju-document/execplans/Issue8 - 商品目录职责恢复.md`。

历史三个 JSON 由 host 全目录（含 ignored/hidden/.scratch/快照）搜索确认缺失；task-input/material-status.json 同样记录不存在。维护者明确授权记录缺口并用新的隔离业务验证替代，未执行旧原库写入驱动。历史报告、issue7 probe 源码只作参考，不作为当前业务 baseline 或本票通过证据。

## 逐组职责结论

| 组 | 当前入口 → Service → 具名 Mapper | 规则/兼容结论 |
| --- | --- | --- |
| 分类 | AdminCategoryController → CategoryApplicationServiceImpl → CategoryService.listByType/pageCatalog → CategoryMapper；UserCategoryController → CategoryService.listEnabled → CategoryMapper | 管理列表包含禁用；用户 status=1，可选 type，type/sort ASC、id DESC；分页 sort ASC、update_time/id DESC。删除按类别检查 DishMapper/SetmealMapper.countByCategory，原规则未改。 |
| 菜品 | AdminDishController、UserDishController → DishApplicationServiceImpl → DishService.listCatalog/pageCatalog → DishMapper | 名称 trim 后包含匹配，可选 category/status；update_time/id DESC；用户只 status=1。套餐关联 countByDishes 拒绝整个删除批次。主键 CRUD 保留。 |
| 口味 | 菜品详情/用户菜品响应 → DishApplicationServiceImpl → DishFlavorService.listByDish/deleteByDishes → DishFlavorMapper | 原未指定口味排序继续保持；更新删除旧口味后批量插入，create/update 公共代理事务覆盖根表和口味；无单独公开口味路径重建。 |
| 套餐 | AdminSetmealController、UserSetmealController → SetmealApplicationServiceImpl → SetmealService.listCatalog/pageCatalog/countSellingInIds → SetmealMapper | 名称/category/status 条件与 update_time/id DESC 保留；用户 list 固定 status=1；售卖套餐不能删除，混合批次整批拒绝。 |
| 套餐明细 | 套餐详情 → SetmealApplicationServiceImpl → SetmealDishService.listBySetmeal/deleteBySetmeals → SetmealDishMapper | 关联明细 id ASC；用户详情继续跳过已不存在菜品，展示当前菜品信息/份数。更新根表与明细在公共 create/update/delete 的 Spring 代理事务内。 |

没有已证据化的全目录功能故障需要发明规则；本次修复明确职责缺口与重复名称异常丢 cause。现有售卖菜品可删（若无套餐关联）、禁用分类不联动其菜品可售状态、套餐详情不另行按根表售卖状态拒绝，均不擅自改变。解析删除批次的现有无效值忽略行为保留。

简单条件只在 Mapper 内用 MyBatis Plus 参数绑定 Wrapper，无复杂 Join/动态聚合，故不新增 XML。既有按状态统计的 DishMapper/SetmealMapper.countByStatus 保留。ApplicationService 管规则、组装编排、事务，薄 ServiceImpl 用构造器注入 Mapper 委派具名条件；BaseMapper 主键 CRUD、IService saveBatch 保留，未机械增层。

合理未改类：AdminCategoryController/AdminDishController/AdminSetmealController/UserDishController/UserSetmealController 的入口协议/身份已经委派服务；Category/Dish/Setmeal ApplicationService 接口、DTO/VO、已有 DishAssembler/SetmealAssembler、entity、JWT interceptor/security、schema 无需修改。订单、AI、算法领域现有查询不属于本票目录管理入口，未扩大改动。

## 两前端消费者

管理端 `src/api/modules/category.ts`、`dish.ts`、`setmeal.ts` 对应 list/page、详情、POST/PUT/DELETE/status；router 的 `/category`、`/dish`、`/dish/add`、`/setmeal`、`/setmeal/add` 使用现有 views。检查了实际页面名称/状态/分页/详情交互，公开 fields/code/token/VITE_API_BASE 均不变，无前端编辑必要。

小程序 `common/vendor.js` 当前适配函数 getCategoryList、dishListByCategoryId、querySetmeaList 调用 `/user/category/list`、`/user/dish/list`、`/user/setmeal/list`，套餐详情 `/user/setmeal/dish/{id}`；首页使用菜品响应 flavors。读取当前发布的两端 HTML API 与数据库设计 category/dish/dish_flavor/setmeal/setmeal_dish 表定义；未恢复历史旧 URL，无 schema、菜单内容/ID、外观或素材变化，因此小程序无需源码修改。

## 自动检查批次与隔离方法

批次 1：checkout root 的 `mvn -B -f yuweiju-backend/pom.xml package`、各自独立 shell 的 admin `npm ci`、`npm run lint`、`npm run typecheck`、`npm run test`、`npm run build`。Java/Maven 使用指定 PATH，非 login shell。六项退出 0；Vitest 2 文件/3 测试通过。Maven 最终源码确认在批次 2：05:17:49 UTC package 退出 0，54 tests、0 failures/errors/skips，其中 CatalogRulesTest 4 项通过。

普通 `CatalogRulesTest`（由上述 package 命令实际执行 `com.codeying.service.impl.CatalogRulesTest`） 检查类别关联拒绝、有关联/无关联混合菜品批次、售卖/停售混合套餐批次、非法分页、duplicate cause。它不声称 Mockito 能证明 SQL 事务。实际事务使用 `.sandcastle/environment/Issue8Probe.java`。

独立验收命令：`bash .sandcastle/environment/issue8-check.sh --browser`。脚本提取候选 jar 的库，编译新探针；`spring.config.location` 替换配置、dev,afk、禁用分析/风控定时任务，模型目录在 `.scratch/issue8`，后端/Vite 只绑定 127.0.0.1:18087/18088。检查连接 URL=mysql:3306/sandcastle_fixture、SELECT DATABASE() 及 redis hostname/db=0 后才写入。使用唯一 i8_ 前缀与本地追踪名字/ID、task-owned conditional MySQL triggers；finally 仅清理拥有的关联/根表行、dev mock 用户、必要时新建的 sandbox_admin 与单一 Redis 键；无原库/清表/flush/标准 schema/卷/真实外部 API 操作。

触发器分别让第二 dish_flavor insert 和第二 setmeal_dish insert SIGNAL；通过 Spring AOP 服务代理调用 update，验证失败 cause，比较全根记录、旧关联 IDs/values/copies 与独立商品哨兵，并确认两个触发器已移除。SQL 回滚不保证自增序列回退，测试未作该承诺。每个场景打印断言结果并写 scenario-results.json，清理后比较预先读取的所有既有目录行，无 hash/冻结 contract/baseline/gate。

浏览器使用 Playwright/系统 Chromium，在真实 Vue 登录页输入公开 synthetic sandbox_admin / sandbox-only-login；三个实际目录视图查询任务前缀，断言业务 records 与 DOM 中的任务商品，并生成截图。网络限制在指定容器 loopback；本轮 dev user login 明确是本地 mock，不是实 WeChat 验收。现有用户目录读取为公开端点；已核对 WebMvcConfiguration 的 exclusions，保留此权限边界。

原始日志与 JSON/截图位于 `.scratch/issue8`，由配置 evidencePaths 保留；最终隔离/浏览器重跑退出 0（约 05:21 UTC）。scenario-results.json 包含 158 条已通过断言、businessPassed=true、cleanupPassed=true；browser-results.json 的真实页面登录及三组目录渲染共 4 项通过。三张截图已逐张检查，任务商品、状态、分类、售价与分页均正常显示。

批次 2 首次隔离检查在目录写入前失败：探针错误地预期用户目录读取会拒绝匿名，实际 WebMvcConfiguration 显式排除了 /user/category/list、/user/dish/list、/user/setmeal/list、/user/setmeal/dish/**。这不是业务故障或未决设计；不能为迁移改变认证。原始 probe-batch2-first.log/scenario-results-batch2-first.json 已保存，finally 清理 mock 用户和 owned Redis 键、确认五个目录表所有既有行未变。修正验收预期：用户目录匿名读取和带无关 scope token 的读取结果保持一致，管理目录仍拒绝缺失/错误 user token；受保护 /user/addressBook/list 验证 user 缺失/错误 admin token 拒绝，正常 mock user 通过。该只读认证边界检查不改地址业务或认证。随后在同一 supervisor iteration 重跑隔离检查，不增加预算/次数。

## 剩余验收与交付

最终代码、SQL/代理回滚、前端与浏览器检查已通过；正常本地业务任务提交已保存：`b3e351fcce6d4801ac35fbf8e53127d71e6db464`。后续记录提交只更新报告/进度，业务源码与已检查的探针保持相同；最终 HEAD 由 Git 元数据给出，供 host 精确候选独立验证。host 必须在新 fixture 数据库上对精确候选做独立验证（不依赖 agent 认证或模型），通过后才准备人工服务。维护者 ALL 人工验收（管理端目录完整交互、小程序开发者工具/实际选择与实登录环境）仍待完成。自动 synthetic PAGE/login/catalog 与本地 mock user 不代替人工验收。无 push、PR、merge、关闭 issue 或原数据操作。

代码回退：由维护者使用正常 Git 流程撤销本票业务提交；不恢复修改过的用户文件，不恢复 PLANS/旧 To-do/Thymeleaf。代码回退不回退已经提交的业务数据/Redis/文件/外部副作用，本轮隔离夹具按 own-only finally 清理。

## 最终资源与检查结果

首次隔离失败无目录写入；最终探针成功创建/更新自己的 5 个分类、4 个菜品及口味、3 个套餐及明细，再全部移除。两次 probe 各自 local mock user 和 owned Redis key 已删除；fixture 已有 sandbox_admin 被只读复用，未改密码/状态/姓名/行。所有 preexisting catalog rows 精确比较未变，触发器 count=0。任务自增序列曾消耗 ID，这是隔离数据库允许且 SQL 回滚不会恢复的效果。没有 Redis flush、原数据写入或外部业务调用。analysis/order-risk 模型路径仅配置到任务 .scratch，未触发训练产物。

脚本退出后确认 Issue8Probe 与 Vite 18088 进程均已结束，未留 coding acceptance 服务；host 人工服务等待独立验证通过后再准备。Maven/npm 下载及本地编译缓存属于本轮授权资源消耗，前端 source/lockfile 未改。git diff --check 与任务范围检查通过，未经 git push/强制/restore/clean，未出现 SANDCASTLE_GIT_BLOCKED。

批次 1 原始输出：maven-batch1.log、npm-{ci,lint,typecheck,test,build}-batch1.log；批次 2：maven-batch2-final.log、check-batch2.log（首次失败）、probe-batch2-first.log、scenario-results-batch2-first.json、check-batch2-rerun.log、probe.log、browser.log、vite.log、scenario-results.json、browser-results.json、synthetic-{category,dish,setmeal}-catalog.png。保留完整失败证据，不以历史检查替代本轮结果。无未完成编码/自动验收项，人工与 host 独立验收仍待进行。

变更文件范围（正常提交逐文件列出）：UserCategoryController；Category/Dish/SetmealApplicationServiceImpl；Category/Dish/DishFlavor/Setmeal/SetmealDish 的 Mapper、Service 接口与 ServiceImpl；CategoryBusinessException/DishBusinessException/SetmealBusinessException cause 构造器；CatalogRulesTest；.sandcastle/environment/Issue8Probe.java、issue8-check.sh、issue8-browser.cjs；本报告、Issue8 ExecPlan、.sandcastle/task-progress.md。其余业务/三端应用文件不变，原始 .scratch 证据不进入源码提交。
