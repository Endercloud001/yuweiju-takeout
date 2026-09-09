# 余味居 Java Code Style Guide

## 适用范围

本规范适用于 `yuweiju-backend` 的 Java、测试、配置映射、Controller、Service、Mapper、Task、WebSocket 和 AI 适配代码。它补充并收敛 `yuweiju-backend/.trae/rules/rules.md`；两者冲突时，以仓库 `AGENTS.md` 和用户明确要求为准。

当前基线是 Java 21、Spring Boot 3.5.x、MyBatis-Plus、MySQL、Redis、JWT、WebSocket 和 Spring AI 1.1.x。Spring AI 2.0 只在 Spring Boot 4.x 独立迁移分支中使用，不能在当前模块直接混入。OpenTelemetry 默认使用 Java agent；只有 Native/GraalVM 等 agent 不适用时才评估 starter。

## 1. 格式与命名

- 使用 4 个空格缩进，不使用 Tab；文件使用 UTF-8。
- 类名使用 PascalCase，方法和变量使用 camelCase，常量使用 `UPPER_SNAKE_CASE`，包名全部小写。
- 布尔变量使用 `is/has/can/should` 前缀，例如 `isPaid`、`hasPermission`。
- 一个方法只承担一个清晰职责；超过 3 个条件分支或 30 行业务编排时，拆成有语义的私有方法或应用服务。
- 类按“公开 API、构造器、公开方法、包级方法、私有方法、内部类型、常量”组织；字段按常量、依赖、配置、状态排列。
- 不使用 `System.out.println`、空 `catch`、魔法数字、无意义的 `Object` 或 `Map<String,Object>` 作为稳定业务接口。
- 禁止为了省行数省略大括号；所有 `if/for/while` 都使用大括号。

```java
private static final int MAX_RECOMMENDATION_LIMIT = 10;

public List<DishSummary> recommend(RecommendationQuery query) {
    validateQuery(query);
    List<Dish> availableDishes = catalogQuery.findAvailable(query);
    return rankDishes(availableDishes, MAX_RECOMMENDATION_LIMIT);
}
```

## 2. 包与依赖方向

新代码按业务模块组织，而不是继续把所有实现放进共享的 `service.impl`：

```text
com.codeying.module.<module>
├── api             # 跨模块 DTO、查询/命令接口、事件载荷
├── application     # 用例编排、事务边界
├── domain          # 业务规则、状态机、值对象
└── infrastructure  # Mapper、Entity、第三方客户端、配置适配
```

- Controller 只依赖 application API，不直接调用 Mapper。
- application 依赖 domain 和 port，不依赖具体第三方 SDK。
- infrastructure 实现 port，可依赖 MyBatis、Redis、微信支付和模型 SDK。
- 模块之间传 DTO/命令/事件，不传 Entity、Mapper、Spring `Page` 或可变集合。
- 过渡期使用 `@ModuleBoundary`/`@OwnedBy` 标注配合 ArchUnit；包迁移完成后才启用 package-based 规则。
- 公共模块只能放稳定的基础类型、异常、结果封装和安全工具，不能成为所有业务的“大杂烩”。

## 3. Controller 与 API

- HTTP 入口使用 REST 语义，统一返回当前项目的 `ApiResult<T>`；不要新建第二套成功/失败包装。
- 请求 DTO 使用 `@Valid`，字段使用 `@NotNull`、`@NotBlank`、`@Size`、`@Positive` 等明确校验。
- Controller 不包含订单状态迁移、价格计算、权限判断、模型 Prompt 或 SQL。
- 管理员和用户 Token scope 必须明确；WebSocket handshake 复用相同身份语义，不自行解析另一套 JWT。
- 路径、字段或响应结构变更必须同步 OpenAPI、HTML API 文档、管理端类型、小程序 wrapper 和 smoke test。
- 错误响应对用户友好，对日志保留 request ID 和 cause；绝不返回堆栈、Key、私钥、完整手机号或支付密文。

```java
@PostMapping("/user/ai-assistant/message/send")
public ApiResult<AiAssistantSendReplyVO> send(
        @Valid @RequestBody AiAssistantMessageSendDTO request) {
    Long userId = currentUserId();
    return ApiResult.success(aiAssistantApplicationService.send(userId, request));
}
```

## 4. Service、事务与状态机

- Application service 表达用户用例，domain service 表达跨实体规则，infrastructure service 只封装外部技术细节。
- `@Transactional` 只放在清晰的 application command 边界；只读查询使用只读事务或不启动事务。
- 订单、支付、客服会话和 AI 消息必须显式定义状态机；状态迁移用允许转换表校验，不能直接 `setStatus` 覆盖。
- 重要写操作使用数据库唯一键防重复；订单更新使用版本号乐观锁，Redis 锁只能作为辅助并发控制，不能替代数据库一致性。
- 重复请求返回首次结果或当前合法状态；版本冲突必须重新读取并返回，不允许最后写覆盖先前合法状态。
- 非关键通知可使用 `@TransactionalEventListener(phase = AFTER_COMMIT)`；不能丢的事件必须在主事务内写入 outbox，提交后 relay 发布并由消费者按 `eventId` 幂等处理，不能在 AFTER_COMMIT 之后才创建唯一 outbox 记录。

## 5. MyBatis 与数据库

- Entity 只反映持久化结构，不放跨模块业务逻辑；DTO/VO 负责 API 结构。
- 简单单表查询可用 MyBatis-Plus wrapper；复杂多表、动态条件、批量操作写 XML，禁止在 Java 字符串中拼 SQL。
- Mapper 参数超过一个时使用 `@Param`；批量插入明确主键回填和事务边界。
- 动态 SQL 使用 `<where>`、`<set>`、`<trim>`，禁止手写 `where 1=1`。
- 金额使用 `BigDecimal`；时间统一使用明确时区的 `LocalDateTime`/`Instant`，禁止在业务中混用默认时区。
- 所有新增索引、唯一键、版本字段和 outbox 表必须同步数据库设计文档、迁移脚本和回滚说明。

## 6. 异常与日志

- 业务异常继承项目统一业务异常基类，使用稳定错误码或消息常量；禁止直接抛裸 `RuntimeException("...")`。
- 全局异常处理器覆盖业务异常、参数校验、数据库约束和未知异常；未知异常对外隐藏细节，对内记录完整 cause。
- 使用 `@Slf4j` 或统一 Logger；日志使用 `{}` 占位符，不使用字符串拼接。
- `INFO` 记录下单、支付、状态迁移、客服发送等关键节点；`WARN` 记录可恢复降级；`ERROR` 必须带异常栈。
- 日志字段优先包含 `traceId`、`requestId`、module、operation、result、latencyMs；禁止把用户输入全文、Prompt、Token、私钥、API Key、支付密文写入日志。
- 外部调用日志记录 provider、HTTP 状态、耗时、重试次数和脱敏请求 ID，不记录完整请求/响应正文。

## 7. 微信支付 v3

- 商户 API 证书/私钥用于商户请求签名；API v3 密钥用于回调敏感字段解密；平台证书或微信支付公钥用于微信响应和回调验签。
- 验签按 `Wechatpay-Serial` 选择对应平台证书/公钥；禁止使用商户 API 证书验微信回调。
- 平台证书更新和商户 API 证书主动更换分别记录序列号、有效期、部署时间、验证结果和撤销时间。
- 支付请求、回调、退款和重复回调必须有幂等键、状态机和审计日志；回调验签失败返回失败并等待微信重试。
- 证书文件只从 Secret/受保护挂载读取；不得提交 PEM、API v3 Key、商户私钥或支付密码。

## 8. Spring AI 与 AI 代码

- 当前代码锁定 Spring AI 1.1.x 与 Spring Boot 3.5.x；2.0 迁移必须先升级 Boot 4.x，在独立 spike 中验证。
- 业务层依赖 `AiModelPort`/`AiAssistantPort`，不直接创建 `RestTemplate`、供应商 SDK 或解析供应商 JSON。
- 模型输出必须经过两层校验：第一层是 JSON Schema/Bean Validation，第二层是业务语义校验，例如菜品可售、归属当前门店、价格仍有效、intent 与实体相容。
- 所有工具调用经过白名单、用户/会话/资源权限和状态校验；高风险动作要求用户二次确认或人工转接。
- Prompt 用版本号管理；用户输入和系统指令分隔；trace 记录版本、模型、Token、延迟、降级和取消计费状态，但不默认保存正文。
- 客户端取消不等于无成本：按已接收 usage/delta 记录 `CLIENT_CANCELLED_BILLABLE` 等状态。
- 工具描述对齐 MCP 的 name、input schema、output、error 语义，为未来 Spring AI 2.0 MCP Server 迁移保留 port，不在当前版本提前耦合 2.0 API。

## 9. WebSocket 与 Redis

- 当前 JVM Map 只保存本节点连接；跨节点状态、输入锁、消息协调通过 Redis，并为消息设计 `messageId`、`sequence`、`originNode`。
- Redis 不可用时客服进入可观察的单节点本地模式，禁止伪造跨节点一致；无法证明锁安全时拒绝管理员发送。
- WebSocket 优先使用短期 ticket，兼容旧 query token 时必须脱敏 URL 日志；握手 authScope 与 HTTP 一致。
- 所有客户端必须处理心跳、指数退避、页面销毁、重复消息和按 sequence 补拉。

## 10. 测试与质量门禁

- 每个新增业务规则先写失败测试，再实现最小代码；测试名称描述业务行为，不描述实现细节。
- 后端至少覆盖单元测试、Spring 集成测试、MySQL/Redis Testcontainers、幂等并发、状态机和外部服务失败。
- API 契约同时有 OpenAPI 静态 lint 和运行时 Schemathesis/等价测试；静态路径匹配不能作为完整契约证明。
- 管理端必须通过 `npm run lint`、`npm run typecheck`、`npm run test`；小程序必须通过配置静态检查、Node wrapper 测试和微信开发者工具编译/人工发布清单。
- CI 必须执行秘密扫描、依赖审计、Maven verify、前端构建、契约测试和容器测试；CI 全绿不替代小程序官方编译。
- 测试不得调用生产支付、真实用户数据或真实模型 Key；模型评测使用 Mock/Ollama/受控测试 Key，并记录成本预算。

## 11. TODO、注释与提交

- 新增 `TODO` 必须注明模块、目的和关联 ExecPlan/Issue；没有关联项时不允许提交 TODO。
- 注释解释“为什么”，不重复代码“做什么”；公共 API 使用 JavaDoc 说明参数、返回值、异常和幂等语义。
- 复杂算法、订单状态机、AI fallback、证书轮换和 Redis 降级必须有设计文档链接。
- 提交保持小而可构建，建议使用 Conventional Commits，例如 `feat(order): add idempotent payment callback`、`fix(ai): validate dish recommendation semantics`。

## 12. 检查命令

在 `E:\Learning Files\yuweiju-takeout` 执行：

```powershell
mvn -q -f yuweiju-backend/pom.xml verify
npm --prefix yuweiju-web-vue/yuweiju-admin run lint
npm --prefix yuweiju-web-vue/yuweiju-admin run typecheck
npm --prefix yuweiju-web-vue/yuweiju-admin run test
gitleaks detect --source . --redact
```

任何规则例外必须在对应 ExecPlan 的 Decision Log 中说明原因、影响、临时期限和删除条件。

## 参考

- [后端原始规则](../../yuweiju-backend/.trae/rules/rules.md)
- [Spring AI 版本兼容](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/getting-started.adoc)
- [微信支付 API v3 概述](https://pay.wechatpay.cn/doc/v3/merchant/4012081606)
- [微信支付平台证书验签](https://pay.wechatpay.cn/doc/v3/merchant/4013053420)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/)
- [Schemathesis](https://schemathesis.readthedocs.io/en/stable/)
- [Shopify Toxiproxy](https://github.com/Shopify/toxiproxy)
