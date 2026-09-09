# 拆分后端业务边界 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans`（或 `superpowers:subagent-driven-development`）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 在不立即拆成微服务、不破坏现有 API 和数据库的前提下，把当前 Spring Boot 单体改造成可独立演进、可测试和可迁移的模块化单体。

**架构：** 采用按业务能力组织的模块边界：身份与访问、菜品与套餐、购物车、订单与支付、客服、AI 助手、分析与风控、平台基础设施。第一阶段仍部署一个 JAR、共享数据库和 Redis；模块之间只通过应用接口和明确 DTO 交互，禁止直接访问其他模块的 Mapper/Entity。后续只有在真实负载和团队边界证明必要时，才提取独立服务。

**技术栈：** Spring Boot 3.5、Maven multi-module 或单模块 package-by-feature、MyBatis-Plus、ArchUnit、JUnit 5、Testcontainers、Spring Modulith（只有在兼容性验证通过后采用其事件和模块检测能力）。

---

## Purpose / Big Picture

当前后端虽然已有 `controller/admin`、`controller/user`、`service`、`mapper`、`entity` 等目录，但 AI 助手、订单、分析、客服之间存在大量直接服务调用；`AiAssistantApplicationServiceImpl` 同时负责会话、意图、推荐候选、天气和模型编排，后续添加语音、工具调用或推荐实验会继续放大耦合。完成后，开发者可以在一个业务模块内定位规则、接口、持久化和测试；跨模块流程通过事件或 facade 连接；现有管理端、小程序和数据库保持兼容。

本计划遵守后端规则中“职责分层、禁止反向依赖、Controller/Service/Mapper 分工”的约束，并会在每个阶段同步核对 API、数据库和 To-do List。参考 `sky-take-out` 的外卖业务闭环，但不照搬其传统分层；采用模块化单体的低风险演进路线。

## Progress

- [ ] 绘制当前依赖图、事务边界和跨模块调用清单。
- [ ] 建立模块接口和架构测试，先让非法依赖可被自动发现。
- [ ] 按风险从低到高迁移目录、Facade、DTO 和 Mapper 所有权。
- [ ] 将跨模块副作用改为事件/应用服务调用并补齐回归测试。
- [ ] 评估模块化单体是否已足够，记录独立服务拆分条件。

## Surprises & Discoveries

- 当前 Maven 只有一个 Spring Boot 工程；直接拆成多个独立服务会同时引入部署、配置、事务和接口版本风险。
- 项目已经存在 `ApplicationService`、`Service`、`Mapper` 和 AI/分析实体，可作为迁移切入点，不需要一次重写所有类。
- 订单支付、库存/菜品状态、客服消息和 AI 推荐都涉及跨模块一致性；不能用简单的 Java package 移动代替边界设计。

## Decision Log

- **Decision：** 首阶段选择模块化单体，不拆微服务。**Rationale：** 共享数据库和当前单实例部署无法证明微服务的收益，先获得边界和测试能力。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 领域实体和 Mapper 的所有权归属具体模块，跨模块只暴露只读 DTO 或命令接口。**Rationale：** 避免 Entity 在模块之间成为隐式公共 API。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 订单状态变化、客服消息、推荐曝光和模型调用采用领域事件/应用事件传递。**Rationale：** 这些副作用不应阻塞核心事务，也便于未来接入队列。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 过渡期先使用 `@ModuleBoundary`/`@OwnedBy` 等职责标注和 ArchUnit 类名规则，包重构完成后再切换到 package-based rules。**Rationale：** 当前多个模块类仍集中在 `com.codeying.service.impl` 等旧包中，过早按包判断会产生假阴性。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 非关键通知默认使用 `@TransactionalEventListener(phase = AFTER_COMMIT)`；订单支付、客服消息和推荐曝光等不能丢的事件先在主事务内写入 outbox 行，再由提交后的 relay/消费者发布、重试和幂等处理。**Rationale：** AFTER_COMMIT 避免未提交事务触发副作用，事务内 outbox 解决“主事务提交但事件发布失败”。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 可靠性计划先完成 WebSocket 分布式协调和握手 adapter，再把 WebSocket 文件归属迁入客服模块。**Rationale：** 两份计划会修改同一个 transport 文件，先做可靠性基线可减少合并冲突和行为漂移。**Date/Author：** 2026-08-08 / Codex。

## Outcomes & Retrospective

成功标准不是目录看起来更整齐，而是 ArchUnit/模块测试能阻止非法依赖，订单主流程测试仍通过，AI/分析可以替换实现而不修改订单 Controller，管理端和小程序端点不变。只有满足这些条件，才考虑把某模块提取为独立服务。

## Context and Orientation

核心源码位于 `yuweiju-backend/src/main/java/com/codeying/`。`controller/admin` 面向 Vue 管理端，`controller/user` 面向小程序，`entity`/`mapper` 对应数据库，`service/impl` 承载业务，`task` 承载分析和风控定时任务，`websocket` 承载实时客服。数据库真相源是 `yuweiju-document/db/db_init.sql` 和 `数据库设计文档.md`；API 真相源是 `yuweiju-document/api/`。

建议的模块依赖方向为：`platform` ← `identity`、`catalog`、`cart`、`order`、`customer-service`、`ai-assistant`、`analytics-risk`；业务模块不依赖 Controller，模块间不共享可变 Entity。箭头表示被依赖方向，不能反向。

## Plan of Work

### 任务 1：建立现状依赖基线

**文件：**

- 创建：`yuweiju-document/architecture/backend-module-map.md`
- 创建：`yuweiju-backend/src/test/java/com/codeying/architecture/ModuleDependencyTest.java`
- 修改：`yuweiju-backend/pom.xml`（仅加入架构测试所需依赖）

- [ ] 按 Controller、Service、Mapper、Entity、Task、WebSocket、配置列出每个业务调用点，重点标记订单到支付、AI、分析、客服的边界。
- [ ] 先定义 `@ModuleBoundary(name = "order")`、`@OwnedBy("order")` 等职责标注，并用类名/注解规则限制 Controller 只能依赖 application/service、Mapper 只能由拥有者访问、AI/analytics 不能依赖 Controller、公共模块不能依赖业务模块；不要把旧包路径当作唯一判断依据。
- [ ] 等任务 2 完成包迁移后，再增加 package-based ArchUnit 规则，并运行“旧包中未标注类清零”检查，防止规则在迁移前失效。
- [ ] 运行测试并记录当前违规项，不通过临时排除隐藏问题；每个排除项写明具体类和迁移任务。

### 任务 2：定义模块契约和目录

**文件：**

- 创建：`yuweiju-backend/src/main/java/com/codeying/module/platform/...`
- 创建：`.../identity/...`
- 创建：`.../catalog/...`
- 创建：`.../cart/...`
- 创建：`.../order/...`
- 创建：`.../customer_service/...`
- 创建：`.../ai_assistant/...`
- 创建：`.../analytics_risk/...`

- [ ] 每个模块建立 `api`、`application`、`domain`、`infrastructure` 四层；`api` 只放跨模块 DTO/接口，`infrastructure` 才能接触 MyBatis Mapper 和外部客户端。
- [ ] 定义核心接口，例如 `CatalogQuery.findAvailableDishes(DishQuery)`、`CartCommand.addItem(AddCartItemCommand)`、`OrderApplication.submit(SubmitOrderCommand)`、`RecommendationQuery.recommend(RecommendationRequest)`；接口返回 DTO，不返回 Entity。
- [ ] 为订单支付、推荐曝光、客服消息定义事件载荷和版本字段，事件只携带必要 ID、状态和时间，不携带敏感 Token。
- [ ] 保留旧 Controller 路径作为 adapter，内部转调新 application service；不在本任务中修改前端字段。

### 任务 3：迁移菜品、套餐、购物车和身份模块

- [ ] 先迁移低风险的 `Category`、`Dish`、`Setmeal`、`ShoppingCart`、登录鉴权代码，移动实现类和 Mapper 所有权，不改变表名或字段。
- [ ] 为 DTO 到 Entity 的转换集中在 assembler，禁止 Controller 手工拼装持久化对象。
- [ ] 为菜品可售状态、套餐明细、购物车用户隔离编写单元测试和 MySQL Testcontainers 集成测试。
- [ ] 运行 ArchUnit，修复迁移模块向旧 `service/impl` 的回退依赖。

### 任务 4：迁移订单、支付和客服模块

- [ ] 将 `OrdersApplicationServiceImpl` 的下单、支付状态、取消、催单流程拆为订单应用服务；支付客户端通过 `PaymentPort` 注入，不让订单规则直接依赖微信 SDK。
- [ ] 在[可靠性计划](./ExecPlan%20-%20补齐生产级可靠性与质量门禁.md)的 Redis 协调、ticket 握手和重连测试完成后，再将 WebSocket 只作为客服模块的 transport adapter；会话权限、消息落库和关闭规则由客服 application service 负责。
- [ ] 为关键事件在订单/客服/分析主事务内写入 outbox 记录；提交后 relay 负责发布失败重试和幂等消费，`@TransactionalEventListener(AFTER_COMMIT)` 只触发 relay/非关键通知。关键事件必须能从 outbox 重放，不能只依赖 JVM 内存事件。
- [ ] 用 `OrderPaid`、`OrderCancelled`、`CustomerServiceMessageCreated` 事件触发统计、通知和推荐曝光，主事务只提交必要业务状态。
- [ ] 对支付回调、重复回调、重复提交和并发状态迁移编写幂等测试。

### 任务 5：迁移 AI、分析和风控模块

- [ ] 将 `AiAssistantApplicationServiceImpl` 拆为 Session、Intent、Recommendation、WeatherContext、ModelGateway 五个可测试组件，接口只接收已校验的命令/查询。
- [ ] 将分析任务和风控任务从订单服务中移出；任务只通过 `OrderReadPort` 或事件读取必要数据，不访问订单模块 Mapper。
- [ ] 推荐曝光、点击、加购和下单结果先写入事务内 outbox，再由分析/AI 消费；消费端按 `eventId` 去重，不能因模型或 Redis 暂时不可用阻塞订单主事务。
- [ ] 为推荐曝光、点击、加购和下单结果定义统一事件，供分析和 AI 评估使用。
- [ ] 以特征版本、模型版本和数据时间窗作为明确字段，运行时模型文件不直接成为业务模块的隐式依赖。

### 任务 6：收敛旧包并决定提取边界

- [ ] 每迁移一个模块删除旧实现的生产引用，禁止保留两个会产生不同结果的并行业务路径。
- [ ] 更新 `yuweiju-backend/.trae/rules/rules.md` 或新增模块规范，说明包命名、依赖方向、事件版本和事务边界。
- [ ] 用 Maven、ArchUnit、outbox 重放测试、接口烟测和三端回归验证；若某模块满足独立数据库、独立扩缩容、独立发布和清晰事件契约四项条件，再单独记录微服务提取 ExecPlan。

## Concrete Steps

工作目录为 `E:\Learning Files\yuweiju-takeout`：

    mvn -q -f yuweiju-backend/pom.xml test
    mvn -q -f yuweiju-backend/pom.xml verify
    powershell -ExecutionPolicy Bypass -File yuweiju-backend/scripts/api_smoke.ps1
    npm --prefix yuweiju-web-vue/yuweiju-admin run lint
    npm --prefix yuweiju-web-vue/yuweiju-admin run typecheck

每次模块迁移后先运行对应测试和 ArchUnit，再迁移下一个模块；不得等待全部迁移完成才首次验证。

## Validation and Acceptance

验收包括：ArchUnit 通过且无未登记违规；所有现有管理端/用户端 HTTP 路径和字段保持兼容；订单下单、支付回调、取消、客服消息、AI 推荐、分析任务和风控任务均有自动化测试；每个模块能通过 application API 测试而不直接访问其他模块 Mapper；重复支付回调不会重复改变状态；三端构建和烟测通过。

## Idempotence and Recovery

目录迁移用小提交完成，每个提交保持可构建。若某模块迁移失败，回滚该模块 adapter 和 package 移动，不回滚数据库结构；事件表/事件字段采用新增兼容方式，旧消费者可以忽略新字段。禁止以“临时共享 Mapper”作为最终状态；若必须过渡，须在依赖测试中登记具体删除条件。

## Artifacts and Notes

计划完成后应产生后端模块地图、ArchUnit 依赖测试、模块 API/事件接口、迁移记录和更新后的架构规则。API 文档、数据库设计、To-do List 和三端影响必须随模块边界变更同步记录。

## Interfaces and Dependencies

- [Spring Modulith](https://spring.io/projects/spring-modulith)：可选的模块边界检测和应用事件能力，使用前确认与 Spring Boot 3.5 的版本矩阵。
- [ArchUnit](https://www.archunit.org/)：以测试形式约束包依赖。
- [Testcontainers](https://testcontainers.com/)：使用真实 MySQL/Redis 容器验证事务和查询。
- [ArchUnit](https://www.archunit.org/)：先用职责标注/类名规则，再切换到包规则。
- [Spring Modulith events](https://docs.spring.io/spring-modulith/reference/events.html)：应用事件和事务提交后的处理模式；使用前确认版本矩阵。
- [可靠性计划](./ExecPlan%20-%20补齐生产级可靠性与质量门禁.md)：WebSocket 分布式协调必须先完成，再进行 transport adapter 归属迁移。
- [sky-take-out](https://github.com/shuhongfan/sky-take-out)：订单、菜品、套餐、支付和小程序闭环参考。
- [后端规则](../../yuweiju-backend/.trae/rules/rules.md)、[数据库设计](../db/数据库设计文档.md)、[API 文档](../api/余味居-用户端接口.html)。
