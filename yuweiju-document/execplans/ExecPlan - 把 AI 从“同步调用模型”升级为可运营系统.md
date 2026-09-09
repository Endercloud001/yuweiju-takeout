# 把 AI 从“同步调用模型”升级为可运营系统 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans`（或 `superpowers:subagent-driven-development`）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 把当前 `RestTemplate + Prompt + 正则/JSON 解析` 的 AI 助手升级为有模型抽象、结构化输出、工具权限、流式体验、限流降级、Prompt 版本、调用追踪和离线评测的生产链路。

**架构：** 第一阶段锁定与当前 Spring Boot 3.5.x 兼容的 Spring AI 1.1.x，使用 `ChatClient`/`ChatModel`、Structured Output 和 Tool Calling 替代自建 HTTP/解析器；Spring AI 2.0 作为面向 Spring Boot 4.x 的迁移目标，不在当前工程中直接混用。可选部署 LiteLLM 作为 OpenAI-compatible 网关统一供应商、预算和路由；用 Langfuse 或 OpenTelemetry 采集 Prompt、模型、延迟、Token、错误和用户反馈。业务模块只依赖 `AiAssistantPort`，不依赖具体供应商。

**技术栈：** Spring AI 1.1.x、DeepSeek/OpenAI-compatible provider、LiteLLM、Langfuse、Redis、SSE/WebSocket、Jackson/Bean Validation、Micrometer/OpenTelemetry、JUnit 5、Testcontainers。

---

## Purpose / Big Picture

当前 `AiModelServiceImpl` 创建本地 `RestTemplate`，`AiAssistantApplicationServiceImpl` 负责意图识别、推荐、天气、历史订单、模型调用和模型输出解析。它能完成演示，但模型供应商切换、超时重试、成本控制、结构化结果校验、Prompt 版本回放和质量回归都缺乏统一入口。完成后，用户仍可在小程序中使用 AI 推荐；管理端可以查看调用状态和质量指标；工程师可以在不修改业务规则的情况下切换模型或回放一次失败请求。

本计划参考 Spring AI 官方项目、LiteLLM、Langfuse 和 JavaGuide 的 LLM API 工程实践。只复用成熟框架和协议，不复制开源项目的密钥、业务数据或未审计代码。AI 不能直接执行支付、退款或任意数据库写入；高风险操作必须回到现有页面和人工客服流程。

## Progress

- [ ] 记录当前 AI 调用链、Prompt、意图枚举、推荐输出和数据访问。
- [ ] 建立 provider-neutral 的模型端口和 Spring AI 适配器。
- [ ] 用结构化输出与工具白名单替换脆弱解析，并保留规则兜底。
- [ ] 增加流式响应、超时/重试/限流/降级、幂等和取消。
- [ ] 接入 Prompt/Trace/成本观测和离线评测集。
- [ ] 完成安全、质量、性能和三端体验回归。

## Surprises & Discoveries

- 当前配置已使用 `spring.ai.openai.*` 命名，但 POM 没有 Spring AI starter，实际调用仍由自建 `RestTemplate` 完成。
- `AiAssistantApplicationServiceImpl` 已有规则优先、模型分类、候选菜品和分析结果融合逻辑；迁移应保留这些确定性业务规则，不要把数据库查询和购物车动作交给模型自由生成。
- JavaGuide 调研强调：生产 LLM 调用需要限流、重试、结构化返回、流式协议、幂等和观测；这与当前长同步请求和 `max-tokens` 固定配置的风险直接对应。

## Decision Log

- **Decision：** Spring AI 负责 Java 内的模型抽象、结构化输出和工具调用；LiteLLM 作为可选独立网关。**Rationale：** Spring AI 适配当前 Boot 3.5；网关能力不应重复在业务代码中实现。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 保留规则引擎作为意图和高风险动作的第一道防线。**Rationale：** 推荐可以由模型润色，支付、退款、地址和订单状态不能由非确定性文本直接驱动。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 先提供同步兼容接口，再增加 SSE 流式接口。**Rationale：** 小程序和管理端可以渐进迁移，避免一次改动三端协议。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** Langfuse 和 OpenTelemetry 采用脱敏 trace，禁止把完整地址、手机号、Token、支付信息写入 Prompt/日志。**Rationale：** AI 观测不能成为新的隐私泄露面。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** `AiToolRegistry` 的工具描述、参数、返回状态和错误语义对齐 MCP Tool/Resource/Prompt 的概念，但第一阶段继续使用后端白名单和本地权限校验；未来迁移到 Spring AI 2.0 的 MCP Server 时保留同一业务 port。**Rationale：** Spring AI 2.x 对应 Spring Boot 4.x，当前不能提前绑定 2.0 API；对齐协议语义可以减少未来整体重写。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 1.1.x 升级必须固定具体 patch 版本并执行 Java-only 依赖矩阵；若未来引入 Kotlin，单独验证 Kotlin 版本与 Spring AI 1.1.x 的兼容性。**Rationale：** 传递依赖可能让 Kotlin 兼容问题在纯 Java 项目中未暴露。**Date/Author：** 2026-08-08 / Codex。

## Outcomes & Retrospective

验收应能从一次真实的 AI 推荐请求中看到：请求 ID、Prompt 版本、模型版本、延迟、Token 用量、客户端取消但已计费的状态、结构化结果和业务语义校验状态以及业务结果；模型超时会降级到规则推荐；非法工具调用不会改变订单；同一个幂等键不会重复写消息或购物车；评测集可在 CI 中比较意图准确率、JSON 合法率、业务语义通过率和推荐点击/加购指标。

## Context and Orientation

模型入口是 `yuweiju-backend/src/main/java/com/codeying/service/impl/AiModelServiceImpl.java`；编排入口是 `AiAssistantApplicationServiceImpl.java`；用户 API 是 `UserAiAssistantController.java`，管理端 API 是 `AdminAiAssistantController.java`；管理端调用位于 `src/api/modules/ai-assistant.ts`，小程序位于 `pages/ai-assistant/index.js`。分析结果和用户历史订单通过后端服务提供候选，购物车写入必须调用既有购物车服务。当前 AI 会话和消息实体/Mapper 已存在，可扩展元数据和调用记录而不重建会话模型。

## Plan of Work

### 任务 1：建立 AI 行为基线和脱敏评测集

**文件：**

- 创建：`yuweiju-document/ai/ai-behavior-baseline.md`
- 创建：`yuweiju-backend/src/test/resources/ai/intent-eval.jsonl`
- 创建：`yuweiju-backend/src/test/resources/ai/recommendation-eval.jsonl`
- 修改：`yuweiju-backend/src/test/java/com/codeying/service/impl/AiAssistantIntentRoutingTest.java`

- [ ] 从现有测试、To-do List 和代码中整理意图枚举、规则优先级、低置信度阈值、推荐候选约束和拒答行为。
- [ ] 写至少 50 条脱敏中文意图样本，覆盖推荐、历史订单、一键推荐、催单、退款、人工客服、地址、购物车和越权请求；每条包含期望 intent、是否允许工具和兜底策略。
- [ ] 写至少 20 条推荐样本，固定候选菜品和过滤条件，禁止把真实用户地址、手机号或订单详情提交到评测集。
- [ ] 运行现有测试，保存基线输出：意图准确率、低置信度回退率、结构化解析失败率和平均耗时。

### 任务 2：引入模型端口和 Spring AI 适配器

**文件：**

- 修改：`yuweiju-backend/pom.xml`
- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/AiModelPort.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/AiRequest.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/AiResponse.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/SpringAiModelAdapter.java`
- 修改：`yuweiju-backend/src/main/resources/application.yml`
- 修改：`yuweiju-backend/src/main/java/com/codeying/service/impl/AiModelServiceImpl.java`

- [ ] 根据 Spring AI 1.1.x 与 Boot 3.5 的兼容矩阵加入最小 starter，并锁定具体 patch 版本；先用 Mock 模型通过测试，再接真实 provider。把 Spring AI 2.0 milestone 只放入独立升级 spike，要求先升级 Spring Boot 4.x 并重新验证 MCP、starter、观测和 Java 21 兼容性。
- [ ] 定义 `AiModelPort.complete(AiRequest)` 和 `stream(AiRequest, AiStreamHandler)`，请求包含 `requestId`、`promptVersion`、`modelProfile`、超时、最大 Token 和结构化 schema 名称。
- [ ] 让 Spring AI adapter 负责 provider 参数、消息格式、usage 提取和错误映射；业务层只看到 `AiResponse`，不看到 `RestTemplate`、HTTP 状态或供应商 JSON。
- [ ] 通过 `modelProfile` 支持默认模型、快速模型和离线测试模型，配置只注入环境变量引用。

### 任务 3：结构化输出和安全工具调用

**文件：**

- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/IntentResult.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/RecommendationResult.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/AiToolRegistry.java`
- 修改：`yuweiju-backend/src/main/java/com/codeying/service/impl/AiAssistantApplicationServiceImpl.java`
- 修改：`yuweiju-backend/src/main/java/com/codeying/controller/user/UserAiAssistantController.java`

- [ ] 用 Jackson/Bean Validation 或 Spring AI Structured Output 将 intent、confidence、dish IDs、reason 和 `needHumanReview` 映射到强类型对象；字段缺失、枚举非法、置信度越界均进入规则兜底。
- [ ] 注册只读工具：查询可售菜品、查询天气摘要、查询用户可见历史订单摘要；每个工具描述对齐 MCP 的 name/inputSchema/output/error 语义。购物车添加只接受后端校验过的 `dishId`、数量和会话确认。
- [ ] 在 schema 校验后增加独立的业务语义校验层：重新查询菜品是否存在、启用、属于当前门店、价格/库存/套餐关系是否仍有效；检查 intent 与实体是否相容、confidence 是否满足该动作阈值。合法 JSON 不等于合法业务动作。
- [ ] 在所有工具执行前经过 `AiToolExecutionGuard`：分隔用户输入与系统指令、重建最小上下文、校验当前用户/会话/资源归属、检查高风险操作是否需要用户二次确认或人工转接；模型输出不能直接调用订单状态、支付、退款、地址写入或任意 URL/SQL。
- [ ] 明确禁止模型直接执行支付、退款、状态变更、地址写入、任意 SQL、外部 URL 请求和管理员操作。
- [ ] 对 Prompt 注入、越权菜品 ID、停用菜品、价格变化、intent/实体矛盾和重复动作编写测试；同时在运行时保留 delimiter/角色分离、工具前置 guard 和人工确认，测试不能替代生产防御。

### 任务 4：可靠调用、流式协议和成本控制

**文件：**

- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/AiCallPolicy.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/AiFallbackService.java`
- 修改：`yuweiju-backend/src/main/java/com/codeying/controller/user/UserAiAssistantController.java`
- 创建或修改：`yuweiju-backend/src/main/java/com/codeying/controller/user/UserAiAssistantStreamController.java`
- 修改：`yuweiju-backend/src/main/java/com/codeying/security/TokenBlacklistService.java`（仅在需要共享请求上下文时）

- [ ] 为用户级、接口级、模型级设置 Redis 令牌桶/并发信号量和每日 Token 上限；429、超时和供应商 5xx 只对安全的幂等请求做指数退避。
- [ ] 为短请求设置有限重试次数和总截止时间；流式请求设置最大连接时长，客户端断开时取消上游调用。
- [ ] 将客户端断开分为 `CLIENT_CANCELLED_BILLABLE`、`CLIENT_CANCELLED_BEFORE_UPSTREAM` 和 `UPSTREAM_CANCELLED`；即使上游取消成功，也按 provider usage/已接收 delta 记录实际产生或可能产生的费用，不能把客户端取消统一记为免费成功。
- [ ] 降级顺序固定为：规则推荐 → 快速模型 → “当前繁忙”可理解提示；降级结果带 `mode` 和 `requestId`，不伪装成正常模型结果。
- [ ] 新增 SSE 事件 `start/delta/tool_call/complete/error`，每个事件包含 `requestId`、sequence 和终止原因；小程序先继续使用同步接口，管理端可先迁移流式展示。
- [ ] 增加幂等键，确保消息、曝光、加购确认和重试不会重复落库。

### 任务 5：Prompt、Trace、成本和评测运营化

**文件：**

- 创建：`yuweiju-backend/src/main/java/com/codeying/ai/AiInvocationRecorder.java`
- 创建：数据库迁移脚本或 SQL：`yuweiju-document/db/migration/Vxx__ai_invocation.sql`
- 创建：`yuweiju-backend/src/main/java/com/codeying/entity/AiInvocation.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/mapper/AiInvocationMapper.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/controller/admin/AdminAiOperationsController.java`
- 修改：`yuweiju-web-vue/yuweiju-admin/src/api/modules/ai-assistant.ts`
- 修改：`yuweiju-web-vue/yuweiju-admin/src/views/inform/index.vue` 或 AI 管理页面

- [ ] 记录 `requestId`、用户/会话脱敏 ID、promptVersion、modelProfile、provider、latency、input/output tokens、cost estimate、status、fallback、schema validation、business validation、tool guard result、cancellationBillingStatus 和业务结果；正文默认不落库，需显式脱敏策略。
- [ ] 用 Langfuse 管理 Prompt 版本、Trace、人工反馈和评测数据集；若部署成本不允许，先用 OpenTelemetry + 自有 `AiInvocation` 表，接口保持可替换。
- [ ] 用 OpenTelemetry Java agent 或 Micrometer 记录 HTTP、Redis、MySQL 和 AI span，并在 trace 中传播 `requestId`。
- [ ] 管理端提供按时间、模型、失败原因、平均延迟、Token、降级率和人工反馈查看的只读页面；不允许从页面直接修改生产 Prompt 而绕过版本审核。
- [ ] 在 Maven 测试中运行评测集，设定可审计阈值：意图准确率、结构化成功率、业务语义通过率、越权工具调用为 0、工具 guard 绕过为 0、超时降级成功率、客户端取消计费记录完整率和成本预算不回归。

### 任务 6：三端回归与上线切换

- [ ] 保持现有同步 API 返回结构，增加可选 `requestId/mode` 字段；先让管理端和小程序验证旧字段，再启用流式入口。
- [ ] 使用 feature flag 按用户/环境启用新 adapter；旧 `AiModelServiceImpl` 只作为短期回滚实现，指标稳定后删除。
- [ ] 执行后端测试、管理端 lint/typecheck/test、小程序开发者工具编译和 AI smoke test；记录真实 provider 的延迟、429、超时、JSON 校验和降级指标。

## Concrete Steps

工作目录为 `E:\Learning Files\yuweiju-takeout`：

    mvn -q -f yuweiju-backend/pom.xml test
    npm --prefix yuweiju-web-vue/yuweiju-admin run lint
    npm --prefix yuweiju-web-vue/yuweiju-admin run typecheck
    npm --prefix yuweiju-web-vue/yuweiju-admin run test
    powershell -ExecutionPolicy Bypass -File yuweiju-backend/scripts/api_smoke.ps1

本地 provider 使用 Mock 或 Ollama；真实 DeepSeek Key 只能来自环境变量，不得写入测试资源或终端日志。

## Validation and Acceptance

模型服务不可用时，AI 接口在截止时间内返回规则推荐或可理解降级；结构化输出非法时不会执行工具；支付/退款/订单状态不会被 AI 直接改变；同步和 SSE 接口均有 request ID；同一个幂等键只产生一条业务动作；Langfuse/OTel 或自有记录能查到 Prompt 版本、模型、延迟、Token 和结果；评测集在 CI 中通过且越权工具调用为 0。

## Idempotence and Recovery

模型 profile、Prompt 版本和 feature flag 可重复切换。上游模型异常时关闭新 profile 即可回到规则/旧 adapter；数据库调用记录只增不改，清理按保留策略执行。新 Schema 必须向后兼容现有客户端；SSE 失败时客户端回落同步接口。旧 Key 撤销前后必须按[凭据计划](./ExecPlan%20-%20立即清理并轮换敏感凭据.md)执行 provider/fallback 全链路验证。禁止用“重试更多次”掩盖供应商限流或成本失控。

## Artifacts and Notes

计划完成后应有 AI 端口、Spring AI adapter、结构化 DTO、工具白名单、调用策略、同步/SSE Controller、调用记录表、Prompt/Trace 接入、评测集和管理端运营视图。任何样本和 trace 都必须脱敏。

## Interfaces and Dependencies

- [Spring AI](https://github.com/spring-projects/spring-ai)：Boot 3.5 对应 1.1.x；提供 ChatClient、Structured Outputs、Tool Calling、Observability 和 Evaluation。
- [Spring AI Getting Started](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/getting-started.adoc)：当前 1.1.x/2.x 与 Spring Boot 3.5/4.x 的兼容矩阵；2.0 不作为当前工程直接依赖。
- [Spring AI MCP Overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)：MCP 迁移时的依赖和传输边界。
- [LiteLLM](https://github.com/BerriAI/litellm)：可选 OpenAI-compatible AI Gateway，提供多供应商统一接口、路由、预算、限流和 guardrails。
- [Langfuse](https://github.com/langfuse/langfuse)：Prompt 管理、Trace、评测、数据集和人工反馈，可自托管。
- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)：以 Java agent 方式采集 Spring/HTTP/数据库等遥测。
- [JavaGuide LLM API 工程实践](https://javaguide.cn/ai/llm-basis/llm-api-engineering.html)：流式、重试、限流、结构化返回、幂等和观测的实践参考。
- [凭据计划](./ExecPlan%20-%20立即清理并轮换敏感凭据.md)：AI Key 轮换、fallback profile 与旧 Key 撤销后的交叉验收。
- [当前模型实现](../../yuweiju-backend/src/main/java/com/codeying/service/impl/AiModelServiceImpl.java) 和 [AI 编排](../../yuweiju-backend/src/main/java/com/codeying/service/impl/AiAssistantApplicationServiceImpl.java)。
