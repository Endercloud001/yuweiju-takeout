# Issue #9 购物车与默认地址职责恢复及验证

2026-10-10；项目根 `/home/agent/workspace`，起点为维护者确认的最新 main `1baefd302c7c96a79dad0233ecc2fe0fb69c4550`，工作区起初干净。读取 `/home/agent/task-input/issue.json`、空 comments、blocked-by（#2 CLOSED）及 `/afk-evidence/iteration-1`；此处仅本轮启动证据，没有旧业务起点提交。宿主历史 cwd/分支未作业务起点。方案在编辑源码之前保存于 `yuweiju-document/execplans/Issue9 - 购物车与默认地址职责恢复.md`。

## 职责链及兼容行为

| 入口/调用者 | Service 规则及事务 | 具名 Mapper |
| --- | --- | --- |
| UserShoppingCartController add/sub/list/clean | ShoppingCartServiceImpl 已有归属/恰好一个商品/可售/口味归一/商品快照/每次一份；保留合规实现，只把数量和单项删除写入加上 owner 条件 | ShoppingCartMapper findItem/findByUser/deleteByUser/updateQuantityOwned/deleteOwned |
| UserAddressBookController create/list/default/detail/update/delete/default switch | AddressBookServiceImpl createForUser/listForUser/defaultForUser/findOwned/updateForUser/deleteForUser/setDefaultForUser；身份、字段及归属在 Service 再校验；create/update/delete/switch 在实际 Spring 代理事务中 | AddressBookMapper countByUser/findByUser/findOwned/findDefaultByUser/findLatestByUser/clearDefaultExcept/updateOwned/deleteOwned/setDefaultOwned |
| OrdersApplicationServiceImpl submit/estimatedDeliveryTime/repetition | submit/estimate 复用 findOwned；repetition 复用 clearForUser；其余结算规则、事务及快照逻辑保留 | 上述 Mapper 经 Service 复用；标准插入/主键 CRUD 保留 BaseMapper |

默认地址规则保持原代码：第一条必为默认；之后 create 仅 isDefault=1 请求替换旧默认；update 忽略请求 isDefault，保留数据库标记；删除默认选剩余最大 ID，不以列表默认顺序挑选；删除最后一条则 default=null、list=[]。phone/detail trim，其他 DTO 字段和类型不变。非法/不存在/他人 ID 对 HTTP 均 code=0，对可复用用例均抛业务异常，无串用户写入。使用现有 OrderBusinessException（BusinessException 子类），使订单入口原有异常分支继续返回“地址不存在”；SQL 异常直接传播并保留 cause。

ShoppingCartChangeDTO 只有 dishId/setmealId/dishFlavor，没有 number。已有数量行为是 add 一份、sub 一份或在 <=1 删除，没有可调数量 API。客户端未知 number（包括负值）不取得数量控制权。已存负数按原代码 add +1、sub 删除，本轮隔离 SQL 验证此事实；MySQL schema 的 number 为 NOT NULL，不为测试改 schema。旧代码的 number=null → add 2 防御分支保留并用普通测试覆盖，不宣称真实数据库可存 null。add 对既有项仍检查商品可售；sub 可移除停售项。新增时存名称/图片/价格，数量变化不改原快照。请求口味 trim；空白/空串/null 合并无口味身份，已有空串记录也复用；有口味和无口味保持不同项。套餐同样保留数量变化前快照。

生产 Mapper 用 MP Wrapper 绑定 user/id/字段值；无拼接请求 SQL。固定 limit 1 保留原排序语义。触发器 DDL 仅插入探针生成的合法唯一名字和数字 ID，条件只作用于本轮合成行。没有新增 application 层、schema、hash、冻结 contract、baseline 或 gate。

## 实际认证、消费者与三端影响

WebMvcConfiguration 对 /user/** 注册 JwtAuthInterceptor，只豁免登录/shop status/catalog 公开读取；购物车和地址上述路由不在豁免清单。拦截器读取配置的 authentication 头、用户签名密钥及 uid，管理员使用另一签名密钥/token 头；从真实配置及拦截器确认边界，不能仅按 URL 前缀认定安全。本票没有修改认证。探针以正常 synthetic admin 密码登录取得管理员 token，分别验证下列真实路由匿名及管理员 token 在 authentication 头均 HTTP 401；正确 synthetic user 才允许自身用例。

| 实际路由 | 匿名/错误管理员 token | 正确用户 |
| --- | --- | --- |
| POST /user/shoppingCart/add、sub | 401 | 自己的商品增减及失败规则 |
| GET /user/shoppingCart/list、DELETE /user/shoppingCart/clean | 401 | 用户列表及仅清自己的行 |
| POST/PUT/DELETE /user/addressBook | 401 | 归属和字段校验、完整写流程 |
| GET /user/addressBook/list、default、/{id} | 401 | 自己的列表/default/detail，外人 ID code=0 |
| PUT /user/addressBook/default | 401 | 只能选自己的 ID |

小程序 app.json 注册 pages/index/index、pages/order/index、pages/address/address、pages/addOrEditAddress/addOrEditAddress。首页/结算逻辑存在 common/vendor.js 共享编译模块：newAddShoppingCartAdd/newShoppingCartSub/getShoppingCartList/delShoppingCart、getAddressBookDefault/getEstimatedDeliveryTime/submitOrderSubmit；地址页及编辑页使用 queryAddressBookList/putAddressBookDefault/queryAddressBookById/addAddressBook/editAddressBook/delAddressBook。公共请求器使用 authentication，成功 code===1。保留全部路径及 DTO/schema，不恢复编译源工程；旧编译包装中的 trailing slash 等调用细节未改动，本轮直接 HTTP 验证既有 Controller 标准路由，原生页面是否完整联动仍待人工。管理端 API 无直接 cart/address 消费；订单用已落库地址快照，两前端均无源码改动，因此无无关 npm 重建。

适用文件：根及后端 AGENTS；docs/agents/workflow.md、domain.md；GLOSSARY；ADR 0001 常规三层、ADR 0002 已有历史图语义；docs/standards/backend.md 的用例/具名 Mapper/构造器/代理事务/因果异常/兼容要求，以及 admin.md 和 miniapp.md 的 API、认证、外观及真实页面验证限制。订单本轮只改三个准备链路委托，不扩展费用规则或历史订单查询图处理；验证提交后的 SQL 快照，不宣称其他票的历史图片查询全域验收。

## 隔离检查和失败证据

新增 `.sandcastle/environment/Issue9Probe.java`、`issue9-check.sh`；命令先实现再运行。脚本取已完成 package 的 BOOT-INF/lib、编译探针，并以真实应用上下文执行。config.location 替换为 application-afk.yml，dev,afk，任务关闭；HTTP 只在 127.0.0.1:18089，无发布编码服务端口。真实 MySQL URL 必须为 mysql:3306/sandcastle_fixture，Redis redis:6379/0，确认后才写。两个登录用户是 dev 的 mock synthetic login，不是实际微信认证；地图是 probe-only Bean 替身返回 7 分钟，不调用真实地图/天气/OSS/pay/model。此替身不取代 SQL、事务代理或 HTTP 认证。

CartAddressRulesTest 是普通规则检查，不是数据库回滚证据。真实探针通过 Spring 代理调用 Service（AopUtils 断言），验证 HTTP 归属及绕开 HTTP 的复用用例归属。具名 MySQL 触发器验证：

- 默认切换：目标 UPDATE 报错条件要求原默认已在同事务清为 0；异常后全量本用户地址及原默认恢复，其他用户不变。
- 默认创建：清旧默认 UPDATE 报错条件要求新默认 INSERT 已可见（本用户已有两个默认）；异常后新行消失，原行/default 不变。
- 默认删除：替代 UPDATE 报错条件要求原地址 DELETE 已可见；异常后原 ID/默认及所有地址完整恢复。
- Cart INSERT/UPDATE 强制报错，检查因果链中的指定 SQL marker，确认行/数量/商品快照及他人购物车不变；核心错误不被统计降级吞掉。
- 本用户 task-owned 推荐 context Redis 键设置 wrong type，使真实 Redis GET 报错；已有统计隔离允许核心 add 成功，键仍保留至 finally 定向删除，不扩展统计领域。

成功路径验证创建默认/更新不覆盖默认/切换/详情/归属/空结果及默认删除选最新 ID；购物车 dish/setmeal/有无口味/停售/不存在/每次一份/快照/scoped clean；现有 HTTP submit 消费自身地址与购物车，SQL order/detail 保存地址及商品快照，清空自己购物车、保留他人购物车，地址后续编辑不改已存订单地址。金额只以当前代码和 packAmount=0 检查，无新增计费、支付或源恢复。

每轮唯一 i9_ 前缀，owned-resources.json 记录合成 ID、键、触发器。finally 移除本轮触发器、用户相关 cart/address/order/detail、自己的商品和模拟用户、自己创建的 admin（已有 synthetic admin 保留）、task-owned Redis 键；逐项确认无残留，并比较原 employee/user/address/cart/catalog/order/risk 表的全部 fixture 行及原 Redis 值/key 集合，包括原 sentinel。无 truncate/原用户 clear/Redis flush/回调自增。SQL 回滚不回滚 Redis、文件、HTTP 或自增序列；间隙允许保留，模型文件在 .scratch/issue9 内。

从项目根执行的精确验收命令：

```sh
export PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH
mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue9-check.sh
```

| Check batch（均 cwd=/home/agent/workspace，UTC） | 命令/退出 | 真实结果与修正 |
| --- | --- | --- |
| 1，08:06–08:12 | package 0；probe 1 | package 62 tests、0 failure/error/skip、5:27 含依赖下载；probe 在业务写入前因风险表实际主键 order_id 与统一 ORDER BY id 不符失败，cleanupPassed=true；修正测试查询 |
| 2，08:12–08:15 | package 0；probe 编译 1 | 最终 Order 准备委托版本 package 62 tests 全通过、2:54、08:14:57 完成；误在重打包结束前调用探针，普通 jar 无 BOOT-INF/lib，未运行 Java/SQL；改为顺序调用 |
| 3，08:15–08:17 | probe 1 | 认证/空态/cart 快照、可售性及跨用户检查通过；测试试图插入 NULL 数量，实际 NOT NULL 约束拒绝，businessPassed=false、cleanupPassed=true；保留 schema，改为普通测试覆盖防御分支 |
| 4，08:17–08:21 | package 0；probe 0 | package 63 tests、0 failure/error/skip、2:54，08:20:53 完成；probe 2026-10-10T08:21:06.825118720Z 至 2026-10-10T08:21:44.336743140Z，206 条业务/清理断言，businessPassed=true、cleanupPassed=true；实际三种默认地址多语句回滚、cart SQL 故障、统计隔离及现有结算通过 |

批次原日志及 JSON 在 `.scratch/issue9`，失败证据保留 batch1/batch2/batch3 和脚本 history；脚本输出只包含脱敏断言，不写 token/HTTP 原始登录响应/凭据。最终 git diff --check 和 bash -n 均 exit=0；scope 检查确认仅授权后端、探针、方案、报告与进度文件改变。提交信息写入 .sandcastle/task-progress.md 与 .scratch/issue9/candidate.json。检查重复按批次记录，未改 supervisor iteration/time 限制。

## 历史限制及人工交付

宿主已搜索完整项目，包括隐藏/忽略/保留快照，未找到 miniapp-cart-add.json 或 miniapp-checkout.json；本轮只能引用 issue/spec/grilling 给出的历史描述，不能读取缺失原 JSON、不能把旧 exit0 当作本轮业务验收。未对原库重跑驱动，使用上述新鲜隔离案例。原生微信开发者工具页面、外观及完整页面联动待人工验收；HTTP/Spring 检查不代替它。管理端源码未变，仍需人工确认订单展示联动。

宿主独立验证必须使用候选实际 commit 与新的 DB/cache，另限 20 分钟；本报告编码检查不代替独立结果。独立通过后由宿主启动并保留隔离人工验收服务。全部人工接受前不 push/PR/merge/sync/close；本地显式任务提交已授权。共享 deadline 09:01:07.613 UTC 和最多两轮不变；每步检查 closing.json，必要 Git 操作被拒绝则记录类别/原因/下一步，保留工作区并停止，不绕过。回退仅以另一个正常授权提交恢复代码；不能恢复被提交业务写入，测试清理仅作用于其自建隔离夹具。
