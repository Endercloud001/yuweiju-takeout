# 余味居架构判断调查

> 2026-10-06 维护更新：本文保留当时方案/调查/验证事实，不作为现行强制规则。PLANS 已退出流程；当前协作与技术规范见仓库 `docs/agents/workflow.md`、`docs/standards/`。旧工具规则和冲突 Code Style 已退役；历史来源名称不再代表执行要求。


调查日期：2026-10-05。范围：回答 Q9，为后续访谈提供候选方案；不修改业务代码、不确定最终路线。已读根目录 AGENTS.md、docs/agents/domain.md、GLOSSARY.md 及后端规则相关条款；docs/adr 尚不存在。

## 已验证的外部证据

**Spring Boot 不强制 Controller／Service／Mapper 三层。** 官方明确不要求特定代码布局，其示例按 customer、order 业务包组织，每包有 Controller、Service、Repository。框架要求、逻辑职责、目录组织应分别讨论。[Spring Boot：Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)

**应用服务不必成为统一的“第四层”。** Service Layer 的职责本就包括向调用端提供操作、协调业务和事务；普通 Service 可以承担应用用例。若另有承载业务规则的领域对象／领域服务，ApplicationService 可以只做编排。Fowler 引述 Eric Evans 的 DDD 定义：应用层负责协调，领域层承载业务规则。因此“应用服务”是职责名称，不能仅凭类名或路径认定多了一层；是否额外拆类须看有没有独立职责。[Service Layer](https://martinfowler.com/eaaCatalog/serviceLayer.html)、[Fowler 引述 DDD 应用层与领域层](https://martinfowler.com/bliki/AnemicDomainModel.html)

**Spring 官方存在其他组织方式。** Petclinic 的 OwnerController 直接注入 OwnerRepository，后者继承 JpaRepository，并同处 owner 包。它是服务端页面示例，不是余味居三端 REST 的直接模板；此例只证明官方示例也不固定三层，不能推出行业采用比例，不能据此取消余味居的业务服务。[OwnerController 源码](https://github.com/spring-projects/spring-petclinic/blob/main/src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java)、[OwnerRepository 源码](https://github.com/spring-projects/spring-petclinic/blob/main/src/main/java/org/springframework/samples/petclinic/owner/OwnerRepository.java)

**模块化单体与分层可同时存在。** Spring Modulith 把模块定义为公开接口、内部实现和所需其他模块接口，默认从应用根包的直接子包发现模块，并允许从简单结构开始。它组织单个 Spring Boot 应用内部的逻辑模块，不意味着每模块独立部署、独立数据库，也不等于必须添加 Maven 模块。[Spring Modulith：Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)

**六边形架构关注内外依赖而非层数。** Cockburn 原文让应用通过端口与 UI、数据库、外部服务交互，适配器负责具体技术，允许测试替代外部设施。它也能画成三层；价值在隔离变化与便于验证，而非把文件夹凑成六个。[Cockburn：Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture)

**浏览器前端无需复制后端三层。** Vue 官方介绍用 Composition API 按逻辑关注点组织代码、用 composable 复用状态逻辑。页面、组件、状态、请求封装可按需要分工；这不是 Controller／Mapper 的同构要求。官方也未计划废弃 Options API，不能把它一律称为过时。[Vue：Composition API FAQ](https://vuejs.org/guide/extras/composition-api-faq.html)

## 余味居候选方案与取舍

以下是结合用户 Q7–Q14 的推断，尚不是采用决定。目标是 Java 后端作品集，必须保留点餐履约、人工客服、AI 推荐、热度分析、异常订单识别；先兼容原 MySQL，两端界面必须保留，仅要求本机与微信开发者工具演示。

| 候选结构 | 适合条件 | 收益与成本 |
| --- | --- | --- |
| 常规分层 | 现有查询和业务职责可逐项整理 | 延续熟悉的 Controller／Service／Mapper；迁移小。业务变多时仍需防止一个 Service 堆满所有功能 |
| 分层内按需应用服务编排 | 下单、客服会话、推荐等用例确有多个协作步骤 | 用例入口、事务及失败处理更清楚；若只是透传同名方法，则增加查找负担 |
| 按业务功能组织的模块化单体 | 跨功能引用已使职责难辨，用户愿意学习模块边界 | 同一业务的入口、服务与查询就近；仍可单应用、共享原库。移动目录及调整依赖有成本，无须先引入 Modulith 工具 |
| DDD／六边形 | 复杂规则难以独立解释和测试，或 DeepSeek 等外部依赖妨碍验证 | 将复杂规则或外部调用隔离；增加模型、映射与接口的学习成本，不宜把所有 CRUD 都套完整结构 |

当前优先调查“保留 Spring Boot／Vue，允许采用 Spring AI，以清晰分层为基础，复杂用例按需编排，外部模型调用集中隔离”的可行性。当前 POM 与模型客户端未发现 Spring AI SDK 接入，配置前缀不代表框架已引入。查询找不到位置应以有业务含义的方法、明确 SQL 入口和调用链解决；并非层数越少越好，也不是添加 ApplicationService 就解决。数据库可继续使用 MyBatis；上述 JPA 示例不构成切换持久化技术的理由。

项目规则 1.2 强制既有分层包组织，2.1 强制 Service／DAO 对外接口，11.1 推荐复杂 SQL 入 XML。按业务包全面调整和富领域模型可能需要明确修订这些规则；不能借官方自由度悄然绕过。此处仅列候选，后续 ExecPlan 应说明三端影响、接口与原库兼容及相关规则。

免费优先和单人学习目标下，暂无证据要求微服务、消息中间件或全面抽象持久化。AI 的真实调用优先、失败后降级是业务要求；隔离模型调用可帮助验证，不意味着本地部署大模型。具体超时、重试与降级实现仍由代码调查确定。

## 决定前的最小验证

1. 选一个复杂订单查询，画出接口到 SQL 的真实路径，确认困难来自分散位置、命名、继承还是职责混合。
2. 选下单与履约用例，列明状态规则、授权和事务，判断普通 Service 是否足够，额外编排是否减少理解成本。
3. 追踪推荐一次成功与一次模型失败的路径，核实真实模型优先、超时、降级及失败对订单的影响。
4. 后续实施前验证三端关键流程。第三轮 Q18 用户选择直接使用原库，不要求独立副本；保持现有 schema 和数据，本轮不连接或修改数据库。不建立新 baseline、contract、gate 或 hash。

尚未验证：用户在不同组织方案下的理解成本、完整本机启动与三端流程、包改动范围、依赖升级兼容性。官方文档与 main 分支会变化；本调查不选定具体最新版，不宣称掌握 2026 年行业普及率。架构判断应由上述具体困难与验证结果支持。

## 访谈后的决定

维护者阅读交互讲解后在 Q9 明确选择常规分层，见 [ADR 0001](./adr/0001-retain-conventional-backend-layering.md)。上文保留为比较依据；按业务包全面改造和完整六边形布局没有被采纳。最终维护路线推荐见 [访谈判断](./yuweiju-judgement-grilling.md)，不代表已授权实施。
