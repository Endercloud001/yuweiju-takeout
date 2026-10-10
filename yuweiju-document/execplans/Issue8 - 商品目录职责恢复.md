# Issue #8 商品目录职责恢复

起点：main e6d62a7379b505713f182caafa45e14ed3c65fcb，issue #8 OPEN，无评论，原生 blocker #2 CLOSED。容器 checkout /home/agent/workspace 为本轮应用根。授权最多 2 轮、总编码 60 分钟，截止 2026-10-10T06:04:35.862Z；最后三分钟保存候选。不得发布或操作原库。

## 实施步骤与职责

1. 读取当前 root/三端 AGENTS、workflow、domain/GLOSSARY、ADR 0001/0002、backend/admin/miniapp 标准、issue 副本和当前 API/数据库文档。实际管理端 api/modules/category.ts、dish.ts、setmeal.ts 和 router/index.ts、views/category、dish、setmeal 消费分页/编辑/启停。小程序 common/vendor.js 当前适配调用 /user/category/list、/user/dish/list、/user/setmeal/list、/user/setmeal/dish/{id}，口味来自菜品响应；历史未用旧路径不恢复。
2. CategoryApplicationServiceImpl 保留类型校验、删除关联拒绝与主键写入；分类 list/page 和用户 enabled list 进入 CategoryMapper 具名方法。UserCategoryController 委派 CategoryService.listEnabled，保留 type/sort/id 次序。
3. DishApplicationServiceImpl 保留创建/更新/删除事务、套餐关联拒绝、默认停售和口味组装；条件读取进入 DishMapper、DishFlavorMapper、SetmealDishMapper。DishServiceImpl/DishFlavorServiceImpl 添加直接委派的具名方法。BaseMapper 主键 CRUD、现有 assembler、DTO/VO 不重写。
4. SetmealApplicationServiceImpl 保留售卖套餐不可删、关联明细顺序与详情现图语义；条件 list/page/count 和明细删除进入 SetmealMapper/SetmealDishMapper。保留公开 create/update/delete 的 @Transactional Spring 代理边界。DuplicateKeyException 保留 cause。非目录算法、订单、AI 等业务不扩展。
5. 新增针对性普通测试；.sandcastle/environment/Issue8Probe.java 使用真实 Spring/MyBatis/MySQL/Redis/HTTP：各组过滤/分页/排序/空/可售/权限；管理员写入用户读取；任务触发器强制第二口味及套餐明细失败，经代理确认根数据与旧关联回滚，独立商品不变。脚本 issue8-check.sh --browser 启动本机 18087/18088，issue8-browser.cjs 正常页面输入 sandbox_admin / sandbox-only-login 并检查目录。先核对 mysql/sandcastle_fixture 与 redis，唯一 i8_ 前缀，finally 仅清理拥有的行/键/触发器，检查哨兵与清理结果；不调用外部业务 API。
6. 文档 docs/issue8-catalog-verification.md 记录代表链、合理未改类、验收业务结果、历史证据缺口及人工验收待办。原始输出 .scratch/issue8；按检查批次记录，不改变监督次数与预算。

## 验收命令（各自 checkout root 独立 shell）

```sh
export PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH
mvn -B -f yuweiju-backend/pom.xml package
cd yuweiju-web-vue/yuweiju-admin && npm ci
cd yuweiju-web-vue/yuweiju-admin && npm run lint
cd yuweiju-web-vue/yuweiju-admin && npm run typecheck
cd yuweiju-web-vue/yuweiju-admin && npm run test
cd yuweiju-web-vue/yuweiju-admin && npm run build
bash .sandcastle/environment/issue8-check.sh --browser
```

检查 git diff --check、范围与显式暂存后正常 local task commit；不得 git add .、push、强制操作或恢复修改文件。遇 SANDCASTLE_GIT_BLOCKED 保存分类/原因/下一步并停止必要动作。

## 三端影响与回退

公开 API/schema/ID/状态及排序不变；管理端和小程序无需代码修改，保留各自 token/authentication 与外观，自动浏览器不代替官方小程序开发者工具和维护者 ALL 人工验收。后端条件迁移不新增层。没有售卖菜品删除拒绝或禁用分类阻止其菜品读取的既有规则，不凭推测添加。代码回退由维护者在正常 Git 流程撤销本任务提交，不回退原数据，不声称 SQL 回滚恢复 Redis/文件/外部请求/自增序列。

历史 miniapp-cart-add.json、api-results.json、browser-routes-results.json 已由 host 全目录确认缺失；维护者授权记录缺口并用新隔离验收替代，禁止重跑原库脚本。
