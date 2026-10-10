# Issue10 服务端可信新单计费

本轮授权：最多 2 个监督轮次，累计 60 分钟（安装、检查、收尾均包含），共享截止 2026-10-10T13:13:36.007Z；宿主独立验证另 20 分钟。模型及 Git/auth guard 不变，无子代理，不自动重启。起点 e6c8af5 为 issue5 未人工接受、未集成的结算源码候选，包含 main 612c637；本票只提交 issue10 差异，后续交付需要两个候选。仅隔离 MySQL/Redis，不写原库，不发布 GitHub。

依据：[workflow](../../docs/agents/workflow.md)、[后端规范](../../docs/standards/backend.md)、[小程序规范](../../docs/standards/miniapp.md)、[管理端规范](../../docs/standards/admin.md)、[ADR0001](../../docs/adr/0001-retain-conventional-backend-layering.md)、[ADR0002](../../docs/adr/0002-preserve-historical-order-images.md)、挂载 issue.json/comments.json/blocked-by.json/authorization.md 和 issue5-order-total-repro.json。#2 已关闭；#5 未接受，本轮维护者明确授权先修复阻碍其验收的金额缺陷。

## 调查事实和最终政策（实施前决定）

复现为隔离订单 4：商品 36、打包 2、存储 38、checkout 44。现有 submit 使用 cart.amount 与请求 packAmount；checkout 固定配送 6。新单按当前可售 dish/setmeal 的 price × 有效数量 + 总份数 × 1 元 + 固定配送 2 元。单份18=21，两份18=40；套餐数量为套餐份数，不展开套餐组成菜品。

数据库设计文档和隔离 schema.sql 的 dish/setmeal.price、shopping_cart/order_detail/orders.amount 为 decimal(10,2)，数量及 pack_amount 为有符号 int。实际库 SHOW CREATE TABLE 及约束在写入前再次核对，证据保留 .scratch/issue10。单价非空、非负，HALF_UP 保留两位，先验证原始及舍入后单价不超过 99999999.99；逐项计算、加包装及配送后总额也不得超过该上限。数量必须是非空正 int；总份数使用 long 累加且不超过 Integer.MAX_VALUE，随后转换 packAmount。零目录单价可以合法下单，至少收取包装和配送；缺失/负价、无商品、双商品身份、无数量、空车、错误购物车归属、无真实用户、无本人地址均拒绝。缓存 cart.amount 不作为价格来源，包括缓存负值/过期值；缺失目录价格绝不默认零。NOT NULL 防御分支以普通 Java 测试覆盖，不迁移 schema 构造非法 SQL。

客户端 amount(BigDecimal)/packAmount(Integer) 类型和路径保留，缺失、过期、负值、篡改声明均忽略。JSON 类型无法解析仍走原协议失败；不因为旧 checkout 44 声明拒绝有效订单。这兼容旧客户端，防止请求收费权下放。配送/餐具默认值在 service 明确提供，避免缺失非费用字段形成 SQL 失败。

## 消费者与兼容性

用户 API HTML 文档列 POST /user/order/submit 的 amount/packAmount、返回 orderAmount；购物车 GET /user/shoppingCart/list 返回单价 amount。实际 vendor 模块24已有 getShoppingCartList，store 模块12已有 initdishListMut，因此无需发明报价接口或费用字段。购物车读取返回当前目录单价，不修改存储购物车；order service 在每次提交时再次取得当前目录单价并保存为明细快照。公有目录价格查询保留匿名可读；购物车/订单由 WebMvcConfiguration + JwtAuthInterceptor 使用 authentication 验证用户，管理端 token 验证管理员。

结算通过 issue5 的 Checkout.vue/checkout.js/build.cjs 重建：进入页面刷新可信 cart，刷新未成功不允许以本地缓存报价提交；提交前再刷新，若金额变化先展示并提示再次确认，避免静默价格变化；提交响应 orderAmount 为最终权威，支付继续使用现有 orderData。以整数分运算避免 JS 浮点累计偏差，两位显示。商品再变价的竞争窗口仍以提交响应为准，当前无锁价/真实扣款接口，不新增锁价协议。

支付/详情/历史/管理端使用存储 orders.amount，不重算历史总额。管理端 src/views/order/index.vue 与 detail.vue 已用 row.amount/detail.amount；无需修改，但执行四项检查。详情 pages/details/index 是旧 webpack 产物，无可编辑 Vue 源；只做必要静态 WXML 配送费文案修正为“以订单合计为准”，保留原布局类名和存储 packAmount，不编造旧单配送2或6。不伪造新生成来源，不编辑 vendor。订单详情读取当前存在刷新历史图片写入，删除该写入并经具名 Mapper 读取历史明细，以符合 ADR0002 和旧单不变要求。

所有本用例条件查询迁至具名 Mapper（购物车/地址已具名；订单归属、历史页、明细、支付订单号）。主键目录/用户 CRUD 可保留。Service 事务保留，订单、明细、清购物车必须原子；推荐及风险沿用现有降级，不扩大业务。

## 实施和检查

先新增真实 submit seam 的普通回归，断言两份18返回40而非38，观察修复前失败。之后实现可信读取、金额边界、checkout 和最小详情修正。复用 issue9 隔离探针模式，新增 Issue10Probe.java/issue10-check.sh：真实 Spring App/代理、MySQL、Redis、HTTP，probe-only 固定地图、显式 dev mock-login，不修改共享安全配置。新用户/菜品/套餐/地址唯一前缀；快照原夹具，按主键/复合键排序。唯一自有触发器使 orders/order_detail 写入失败，检查 SQL 回滚、购物车、另一用户及 Redis。finally 只删除自有资源并核对原夹具，无 flush/清表/重置序列。自动增长空洞不视为回滚失败。

精确检查依赖顺序（均为检查批次，不增加监督轮次）：

```sh
export PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:$PATH; mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue10-check.sh
cd yuweiju-weixin-miniapp/checkout-source && npm ci --cache ../../.scratch/issue10/npm-cache --no-audit --no-fund && npm run build && npm test
cd yuweiju-web-vue/yuweiju-admin && npm ci --cache ../../.scratch/issue10/admin-npm-cache --no-audit --no-fund && npm run lint && npm run typecheck && npm run test && npm run build
python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . yuweiju-document/execplans/Issue10-trusted-order-charging.md docs/issue10-billing-verification.md
```

实际 cwd/exit/场景和历史、agent、独立验证差别记录 [验证报告](../../docs/issue10-billing-verification.md)。官方微信编译、REAL 隔离 HTTP 截图、人工验收由宿主后续执行，agent 模拟不能代替。每组更新 .sandcastle/task-progress.md 与 .scratch/issue10；检查 /afk-evidence/closing.json，最后180秒保存候选/剩余事项。通过检查后 diff --check、范围审查、显式 stage、普通本地提交。

## 回退及未决替代方案

代码回退可使用正常反向提交；不追改既有订单，不撤销已提交业务数据。无需 schema 迁移、新 fee columns、真实支付或退款。拒绝客户端 stale claims 会打断 issue5/旧客户端，因此选择忽略；接受客户端 amount 或后端仅加6均无法解决可信收费。历史配送金额不能从差额推定，采用合计准文案。若实际库与文档的范围不一致或隔离归属未知，停止写入并记录；没有普通选择需再次批准。Git blocked 保留工作区，记录类别/原因/下一步，必要操作被阻止则不发 COMPLETE。
