# Issue #9 购物车与默认地址职责恢复

2026-10-10，本轮授权实现、本地显式任务提交及隔离检查；根目录 `/home/agent/workspace` 是容器项目目录，HEAD 起点 `1baefd302c7c96a79dad0233ecc2fe0fb69c4550`。宿主 `.scratch/issue9-afk-20261010` 不作为应用根。截止 09:01:07.613 UTC；至多两轮共享 60 分钟，独立验证另限 20 分钟，不更改限制。#2 已关闭，comments 为空；/afk-evidence 当前仅启动记录，无之前业务实现提交。

## 当前职责与实施

- `UserShoppingCartController` → `ShoppingCartServiceImpl` → `ShoppingCartMapper` 已实现用户归属、恰好一个 dish/setmeal、添加前可售检查（已有项也检查）、口味 trim/空/null、名称/图片/金额首次快照、每次一份及减至一份删除、按用户列表/清空。保留合规路径，不重新实现。`ShoppingCartChangeDTO` 无数量字段；传入 number 被现有 JSON 映射忽略，非可调数量 API。已有负数记录在 add 按原值 +1、sub 删除；不引入新产品规则。检查剩余写条件，必要时在具名 Mapper 中绑定 user/id 更新数量、删除；标准插入和主键 CRUD 仍用 BaseMapper。
- `UserAddressBookController` 中规则和事务移到 `AddressBookService` / `AddressBookServiceImpl`，构造器注入 `AddressBookMapper`。具名方法覆盖 user list（default desc/id desc）、default/latest（id desc limit 1）、count、owned lookup、clear others、owned update/default/delete。Controller 仅身份/参数/响应。所有业务归属和必需 phone/detail 检查在可复用 Service 中成立，保留 trim，默认首地址、create isDefault=1 清旧默认、update 忽略 isDefault、删除默认选最新剩余 ID、最后一个删除无默认的现有行为。多语句事务在 Service 代理边界，核心 SQL 异常保留 cause、不吞失败。
- `OrdersApplicationServiceImpl` 保留既有地址归属、cart、可售、金额及订单明细快照消费。必要时地址条件查询委托具名 Mapper；不扩展计费、统计或认证。

## 三端、兼容及标准

遵循 docs/agents/workflow.md、domain.md、GLOSSARY、ADR 0001/0002、docs/standards/backend.md、admin.md、miniapp.md 和后端 AGENTS。小程序 app.json 注册首页、order、address、addOrEditAddress；实际 common/vendor.js 请求 `/user/shoppingCart/add/sub/list/clean`、`/user/addressBook` CRUD、list/default/{id}、`/user/order/submit`，authentication 头。WebMvcConfiguration 实际保护这些路由，不在商品公开读豁免内；匿名/管理员 scope 应拒绝，正确用户只可读写自身。管理端无直接 cart/address 消费，订单使用已存地址/商品快照。API、DTO 字段类型、业务 code、schema、ID、排序、认证均保持，两前端无需源码修改，无 npm 重建。原生小程序页面操作待人工；不恢复源工程。

## 验证与证据

先新增并审查 `.sandcastle/environment/Issue9Probe.java` 与 `issue9-check.sh`，参考 Issue7/Issue8 patterns，真实 Spring/MyBatis/MySQL/Redis/HTTP。以 config.location 替换为 `.sandcastle/environment/application-afk.yml`、dev,afk、关闭任务、loopback 临时 HTTP、所有文件放 `.scratch/issue9`。写入前核对 MySQL `mysql:3306/sandcastle_fixture`、Redis `redis:6379/0`。仅 task-owned 唯一前缀、两个 mock synthetic users，正常 synthetic admin 登录仅测 wrong scope。原有 schema、sentinel/fixture 完整比对；追踪 rows/keys/triggers 并 finally 定向清理，无 flush/truncate/reset。

普通测试验证地址复用拒绝及规范化/default 不可覆盖、cart 关键边界；Mockito 不算事务证据。真实代理与 SQL 验证成功/空/归属/可售/口味/商品快照、地址全 CRUD/default、结算保留路径。条件触发器在 default switching、default creation、default replacement deletion 前语句成功后强制失败；cart insert/update 强制失败并断言状态/快照未变、cause 可见。保留当前统计隔离，不扩域。SQL 回滚不保证 Redis、文件、HTTP、自增序列回退。

从项目根精确执行：

```sh
export PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH
mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue9-check.sh
```

重复检查按 check batches 记录真实 cwd/time/exit/scenario results。历史 miniapp-cart-add.json / miniapp-checkout.json 已由宿主搜索全项目含隐藏/忽略/保留快照无命中；明确证据限制，不运行原库驱动。业务 JSON businessPassed/cleanupPassed 仅在断言成功后为 true。报告 `docs/issue9-cart-address-verification.md`，进度 `.sandcastle/task-progress.md`。每步检查 /afk-evidence/closing.json，末 180 秒优先保存提交/进度/未完成项。

## 回退与交付

显式 stage 授权任务文件、git diff --check、正常本地 commit；不 push/PR/merge/sync/close，全部人工接受后才能发布。代码回退用另一个已授权正常提交，不能 hard reset/强制 checkout/覆盖修改文件；已提交业务写入不会被代码回退撤销，测试只清自己的隔离夹具。若 SANDCASTLE_GIT_BLOCKED，保存工作区记录类别、原因、下一步，不绕过；必要操作被阻断则不宣称 COMPLETE。宿主在独立验证通过后启动并保留独立人工验收服务，编码检查不代替独立验证或人工原生页面验收。

## 实施后的代码事实补充

OrdersApplicationServiceImpl 最终仅三处准备链路委托修改（submit/estimate findOwned、repetition clearForUser）；其余订单逻辑无 diff。地址用例采用当前 OrderBusinessException 子类，维持订单入口原有 地址不存在 业务响应。实际 MySQL number 为 NOT NULL；null 防御分支只由 CartAddressRulesTest 普通测试覆盖，不造不兼容数据库状态。最终新增测试类为 com.codeying.service.impl.CartAddressRulesTest；探针及精确命令如上。批次失败及修正记录于 docs/issue9-cart-address-verification.md，原证据保留 .scratch/issue9。
