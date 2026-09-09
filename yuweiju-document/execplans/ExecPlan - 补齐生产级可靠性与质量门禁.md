# 补齐生产级可靠性与质量门禁 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans`（或 `superpowers:subagent-driven-development`）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 让三端具备可重复构建、可观测、可回归和可水平扩展的生产质量基线，重点解决内存 WebSocket 状态、重复操作、缺失测试、缺少 CI、无健康指标和构建产物混入仓库等问题。

**架构：** 保留 Spring Boot 单体部署形态，但把 WebSocket 连接作为节点本地资源，把会话、客服锁、在线状态和跨节点消息通过 Redis/消息机制协调；支付、下单、客服、AI 请求使用幂等键和可恢复状态。CI 通过 Maven、npm、契约检查、秘密扫描、容器集成测试和前端测试形成门禁，OpenTelemetry Java agent + Actuator/Micrometer 提供运行时观测。

**技术栈：** Redis Pub/Sub 或 Streams、Spring WebSocket、Spring Boot Actuator、Micrometer、OpenTelemetry Java agent、JUnit 5、Testcontainers、Vitest、微信开发者工具编译、Playwright（管理端关键路径可选）、GitHub Actions、Gitleaks、Dependency Review。

---

## Purpose / Big Picture

当前客服 WebSocket 使用 JVM 静态 Map 保存连接和回复锁；多实例部署时用户连接到不同节点会丢消息或锁状态。后端测试覆盖 AI 意图、分析和订单风险的一部分，管理端只有少量 Vitest 测试，小程序没有成体系的自动化测试；项目也缺少统一 CI、健康检查和链路追踪。完成后，节点重启、短暂 Redis/模型故障、重复支付回调、重复客服消息和客户端重连都有可验证行为；每个 Pull Request 都经过统一质量门禁。

计划遵守三端规则、API/DB 文档和 To-do List。实时客服、AI 助手和分析任务是当前重点，因此可靠性测试必须覆盖这些跨端场景，而不是只做编译检查。

## Progress

- [ ] 定义 SLO、故障矩阵、幂等键和可观测字段。
- [ ] 改造 WebSocket 分布式状态、鉴权握手和重连。
- [ ] 增加 Actuator、指标、Trace、结构化日志和敏感字段脱敏。
- [ ] 建立后端容器集成测试、管理端测试和小程序静态/编译门禁。
- [ ] 建立 CI、依赖/秘密扫描、构建产物策略和发布回滚演练。

## Surprises & Discoveries

- `CustomerServiceWebSocketServer` 使用 `ConcurrentHashMap` 只能解决单 JVM 并发，不能解决节点间路由和广播。
- WebSocket Token 当前经查询参数传递；必须在保留兼容窗口的同时减少令牌暴露在 URL、代理访问日志和历史记录中的机会。
- 管理端 API 超时和 WebSocket 重连逻辑分散在页面；可靠性修复应先抽公共策略，避免每页出现不同退避算法。
- 小程序目录含 `common/vendor.js` 和 `node-modules`，这些生成/依赖内容不应纳入质量修改范围。

## Decision Log

- **Decision：** 第一阶段使用已有 Redis 做客服锁、在线状态和消息协调，不立即引入 Kafka。**Rationale：** 当前规模和已有依赖适合 Redis；只有消息吞吐、持久化和回放需求超过 Redis 能力时再引入独立消息平台。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 采用 OpenTelemetry Java agent 先做零侵入基础追踪，再为 AI、支付和订单补业务 span。**Rationale：** 开源 agent 已覆盖 Spring/HTTP/数据库，减少重复埋点。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 默认使用 OpenTelemetry Java agent；只有进入 Spring Native/GraalVM 等 agent 不适用的部署形态时，才评估 Spring Boot starter，并保持 OTLP/Collector 作为厂商无关出口。**Rationale：** OpenTelemetry 官方当前将 Java agent 作为多数 Spring Boot 应用的默认方案，starter 更适合特定场景。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** CI 质量门禁分为必须通过和允许告警两级；微信开发者工具编译作为发布前门禁，不能伪装成普通 Node 单元测试。**Rationale：** 小程序真实编译依赖官方工具环境。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** Redis 不可用时，客服明确降级为“单节点本地连接路由 + 禁止跨节点广播/输入锁租约”，不会静默假装分布式一致；如果当前连接无法证明会话锁安全，则拒绝管理员发送并提示重试。**Rationale：** 单节点有限可用性比错误地双发或并发回复更安全，但必须让运维和用户知道降级状态。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 幂等冲突采用“数据库唯一键拦截 + 订单版本号乐观锁 + 必要时短 TTL Redis 锁”的组合；重复请求返回首次结果，版本冲突重新读取订单并返回当前状态，不用最后写入覆盖先前状态。**Rationale：** 支付回调和用户查询/操作可能并发到达，单独依赖 TTL 锁会在过期或 Redis 故障时失效。**Date/Author：** 2026-08-08 / Codex。

## Outcomes & Retrospective

成功标准是：单节点和双节点测试都能正确路由客服消息；Redis 不可用时单节点降级行为、跨节点禁用状态和告警可观察；网络分区/脑裂测试有明确结果；客户端断线重连不会重复显示或重复落库；订单/支付/客服/AI 关键操作具备唯一键、版本冲突和重复请求语义；Actuator、指标和 trace 能定位一次请求；Pull Request 在秘密扫描、后端测试、前端质量、契约检查和容器测试任一失败时被阻断。CI 全绿不代表小程序已完成官方编译，发布仍必须完成微信开发者工具编译和人工发布清单；发布可以回滚到上一版本并恢复数据一致性。

## Context and Orientation

后端 WebSocket 代码在 `yuweiju-backend/src/main/java/com/codeying/websocket/CustomerServiceWebSocketServer.java`，定时释放锁在 `task/CustomerServiceReplyLockTask.java`；Redis 已在 `pom.xml` 和 `application.yml` 配置。HTTP 鉴权在 `interceptor/JwtAuthInterceptor.java`，全局异常在 `handler/GlobalExceptionHandler.java`。后端测试在 `src/test/java`；管理端脚本在 `yuweiju-web-vue/yuweiju-admin/package.json`；小程序页面在 `pages/`，入口为 `app.js`。现有 API smoke 脚本是 `yuweiju-backend/scripts/api_smoke.ps1`。

## Plan of Work

### 任务 1：定义可靠性基线、SLO 和幂等模型

**文件：**

- 创建：`yuweiju-document/operations/reliability-baseline.md`
- 创建：`yuweiju-document/operations/failure-matrix.md`
- 修改：`yuweiju-document/api/websocket-events.md`

- [ ] 定义关键指标：登录成功率、下单成功率、支付回调重复率、客服消息送达率、AI 超时/降级率、P95 延迟和错误预算。
- [ ] 为下单提交、支付回调、订单取消、客服发送、AI 消息和购物车确认定义幂等键来源、存储 TTL、重复请求响应和并发冲突响应。
- [ ] 写出故障矩阵：Redis 不可用、数据库慢、模型 429/5xx、WebSocket 节点重启、网络断开、客户端重复点击；每项说明用户提示、重试策略、告警和人工恢复。
- [ ] 对每类幂等键写出冲突算法：先由数据库唯一键保证同一业务键只有一条记录；订单更新使用 `where id = ? and version = ?` 的乐观锁；版本冲突时重新读取状态并返回首次/当前结果；支付回调与主动查询并发时只允许合法状态机迁移，禁止最后写覆盖。
- [ ] 明确 Redis 故障矩阵：客服进入单节点本地路由模式，禁止跨节点广播和新输入锁租约；已有锁在无法续期时过期并拒绝发送；下单/支付只按各自数据库唯一键和版本锁继续，依赖 Redis 的非核心推荐/在线状态进入降级。

### 任务 2：改造客服 WebSocket 的分布式协调

**文件：**

- 修改：`yuweiju-backend/src/main/java/com/codeying/websocket/CustomerServiceWebSocketServer.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/websocket/CustomerServicePresenceService.java`
- 创建：`yuweiju-backend/src/main/java/com/codeying/websocket/CustomerServiceMessageBus.java`
- 修改：`yuweiju-backend/src/main/java/com/codeying/config/WebSocketConfiguration.java`
- 修改：`yuweiju-backend/src/main/resources/application.yml`
- 修改：`yuweiju-web-vue/yuweiju-admin/src/views/customer-service/index.vue`
- 修改：`yuweiju-weixin-miniapp/pages/customer-service/index.js`

- [ ] 将静态 Map 限制为当前节点的连接索引；管理员锁、用户在线 TTL、节点 ID 和消息路由放入 Redis，使用原子 set-if-absent/续期/释放。
- [ ] 使用 Redis Pub/Sub 或 Streams 广播跨节点消息；消息包含 `messageId`、`sessionId`、`sequence`、`originNode` 和事件类型，客户端按序去重。
- [ ] 增加握手鉴权拦截器：优先接受短期 WebSocket ticket，兼容窗口内接受旧 query token；禁止在普通业务日志中打印完整 URL。
- [ ] 暴露降级状态指标 `customer_service.distributed_mode=degraded`；Redis 恢复后通过 session/message list 补偿，不自动把本地期间的锁和消息当作已跨节点同步。
- [ ] 管理端和小程序采用指数退避、最大重连次数、心跳、页面销毁清理和消息去重；连接恢复后按 `lastSequence` 补拉消息列表。
- [ ] 双节点集成测试验证用户在 A、管理员在 B 时，消息、输入锁、关闭会话和断线重连均正确。

### 任务 3：加入健康检查、指标和 Trace

**文件：**

- 修改：`yuweiju-backend/pom.xml`
- 创建：`yuweiju-backend/src/main/resources/application-observability.yml`
- 创建：`yuweiju-backend/src/main/java/com/codeying/config/ObservabilityConfiguration.java`
- 修改：`yuweiju-backend/src/main/java/com/codeying/handler/GlobalExceptionHandler.java`
- 修改：`yuweiju-backend/src/main/java/com/codeying/websocket/CustomerServiceWebSocketServer.java`

- [ ] 加入 Actuator、Micrometer Prometheus registry 和必要的 health contributor；`readiness` 检查数据库/Redis，外部模型检查不阻塞核心就绪。
- [ ] 使用低基数标签记录 HTTP status、业务模块、操作、WebSocket event、AI model profile 和 fallback；禁止把用户 ID、订单号全文、Prompt 正文和 Token 放入标签。
- [ ] 通过 OpenTelemetry Java agent 采集 Spring/HTTP/数据库/Redis trace，并对订单支付、客服发送、AI 调用增加脱敏业务 span。
- [ ] 日志统一包含 `traceId`、`requestId`、模块、结果和耗时；异常日志保留 cause，敏感字段脱敏。
- [ ] 增加 `/actuator/health/readiness`、`/actuator/health/liveness` 和 `/actuator/prometheus` 的访问控制与部署配置。

### 任务 4：后端容器集成和故障测试

**文件：**

- 创建：`yuweiju-backend/src/test/java/com/codeying/reliability/RedisWebSocketReliabilityTest.java`
- 创建：`yuweiju-backend/src/test/java/com/codeying/reliability/OrderIdempotencyIntegrationTest.java`
- 创建：`yuweiju-backend/src/test/java/com/codeying/reliability/AiFallbackIntegrationTest.java`
- 修改：`yuweiju-backend/pom.xml`

- [ ] 用 Testcontainers 启动 MySQL/Redis，验证 Mapper、事务、锁 TTL 和消息去重；测试不依赖开发机服务。
- [ ] 模拟重复订单提交和支付回调，断言只生成一次订单状态变更和一次业务事件。
- [ ] 模拟 Redis 短暂不可用、模型 429/超时、数据库慢查询，断言超时、降级和用户错误提示符合故障矩阵。
- [ ] 使用 Shopify Toxiproxy 或等价 TCP 故障注入工具模拟 Redis 双向延迟、断开、单向丢包和恢复；验证两个后端都存活但彼此无法通过 Redis 通信时，不会双发消息、不产生两个有效管理员锁，并进入预期的单节点/拒绝策略。
- [ ] 分别测试“支付回调先到、用户查询后到”和“用户操作先到、支付回调后到”的并发顺序，断言数据库唯一键、版本号和订单状态机的最终结果一致。
- [ ] 对定时分析、风控和客服锁任务验证重复执行不会重复生成模型文件、重复发送消息或覆盖新锁。

### 任务 5：补齐管理端和小程序质量门禁

**文件：**

- 创建：`yuweiju-web-vue/yuweiju-admin/src/__tests__/http.test.ts`
- 创建：`yuweiju-web-vue/yuweiju-admin/src/__tests__/customer-service-reconnect.test.ts`
- 创建：`yuweiju-weixin-miniapp/test/http.test.js`
- 创建：`yuweiju-weixin-miniapp/scripts/validate-config.mjs`
- 修改：`yuweiju-web-vue/yuweiju-admin/package.json`

- [ ] 管理端测试覆盖 ApiResponse 解析、401 清理登录、Blob 下载、网络超时、WebSocket 重连和消息去重。
- [ ] 小程序 wrapper 测试覆盖用户 Token、请求失败、环境 URL、WSS 强制、重连和重复消息；不测试生成的 vendor 文件。
- [ ] `validate-config.mjs` 检查生产 URL 是 HTTPS、客服 URL 是 WSS、必需路径存在且不存在 `localhost` 硬编码。
- [ ] 保持并执行 `npm run lint`、`npm run typecheck`、`npm run test`；小程序使用微信开发者工具进行编译和关键页面回归。

### 任务 6：建立 CI、依赖和发布门禁

**文件：**

- 创建：`.github/workflows/ci.yml`
- 创建：`.github/workflows/security.yml`
- 创建：`.github/dependabot.yml`
- 创建：`yuweiju-backend/Dockerfile`
- 创建：`yuweiju-backend/README-operations.md`

- [ ] CI 分阶段运行：秘密扫描、Maven test/verify、Testcontainers 集成测试、管理端 lint/typecheck/test/build、API contract check、小程序配置静态检查。
- [ ] 使用 Dependabot/Renovate 或平台等价工具提依赖更新；启用 Dependency Review，锁定 Maven/npm lockfile，升级前运行完整矩阵。
- [ ] 使用 OTel Java agent 的固定版本；镜像使用非 root 用户、只复制构建产物和配置模板，不复制 `.env`、证书、运行时模型和 `target` 临时文件。
- [ ] 发布采用不可变镜像、数据库迁移前备份、灰度/健康检查和上一版本回滚；回滚不逆向删除已执行的兼容数据库字段。
- [ ] 用 Gitleaks 扫描源码和历史，失败时阻止合入；安全报告只上传脱敏结果。

### 任务 7：演练和验收

- [ ] 本地单节点执行全量测试和 API smoke。
- [ ] 测试环境启动两个后端实例，执行客服跨节点、订单重复提交、模型降级和节点滚动重启。
- [ ] 观察 Actuator、指标、日志和 trace，确认一次失败请求可从前端 request ID 定位到后端和外部模型调用。
- [ ] 执行发布回滚演练，确认旧版本仍能读取新增兼容字段，客服和订单数据无重复或丢失。

## Concrete Steps

工作目录为 `E:\Learning Files\yuweiju-takeout`：

    mvn -q -f yuweiju-backend/pom.xml verify
    npm --prefix yuweiju-web-vue/yuweiju-admin ci
    npm --prefix yuweiju-web-vue/yuweiju-admin run lint
    npm --prefix yuweiju-web-vue/yuweiju-admin run typecheck
    npm --prefix yuweiju-web-vue/yuweiju-admin run test
    npm --prefix yuweiju-web-vue/yuweiju-admin run build
    node yuweiju-weixin-miniapp/scripts/validate-config.mjs
    gitleaks detect --source . --redact

小程序编译由微信开发者工具执行并保存版本号、编译结果和关键页面测试记录；不能用 Node 脚本替代官方编译。

## Validation and Acceptance

CI 必须在秘密扫描、后端测试、集成测试、管理端 lint/typecheck/test/build、契约检查和配置校验全部通过后才能合并。双节点客服测试通过；断线恢复后无重复消息；订单和支付操作幂等；模型故障能降级；健康检查能区分存活和就绪；指标/trace/日志不含敏感数据；发布和回滚演练有可审计输出。

## Idempotence and Recovery

测试、构建、扫描和 Docker 构建可重复。Redis Pub/Sub 消息丢失时必须通过消息列表补偿，不能把 Pub/Sub 当作唯一持久化。数据库迁移只采用向前兼容；旧版本回滚通过应用回滚和保留字段实现。若新观测系统不可用，业务仍应运行并降级为本地结构化日志；若 Redis 不可用，客服明确切换单节点本地模式并禁用跨节点广播/租约锁，无法证明安全时拒绝发送；核心下单按数据库唯一键和版本锁处理，不能静默放开并发风险。

## Artifacts and Notes

计划完成后应产生可靠性基线、故障矩阵、Redis/WebSocket 协调组件、Actuator/OTel 配置、容器集成测试、管理端/小程序测试、CI/security/dependency 配置、Dockerfile 和运维手册。所有新增告警必须关联具体 SLO 和处理步骤。

## Interfaces and Dependencies

- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)：零侵入 Java agent 和 OTLP 导出。
- [OpenTelemetry Spring Boot starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)：Spring Boot 无代码/低侵入接入方式。
- [OpenTelemetry Spring Boot instrumentation](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/out-of-the-box-instrumentation/)：常见 Spring 库的自动 traces/metrics/logs 接入范围。
- [Testcontainers](https://testcontainers.com/)：真实 MySQL/Redis 集成测试。
- [Shopify Toxiproxy](https://github.com/Shopify/toxiproxy)：可重复注入延迟、断开、丢包和单向网络故障，覆盖脑裂/网络分区测试。
- [Gitleaks](https://github.com/gitleaks/gitleaks)：秘密扫描和 CI/pre-commit 门禁。
- [GitHub Actions](https://docs.github.com/actions)、[Dependency Review](https://github.com/actions/dependency-review-action)：构建和依赖安全门禁。
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html)：健康检查和指标暴露。
- [现有客服 WebSocket](../../yuweiju-backend/src/main/java/com/codeying/websocket/CustomerServiceWebSocketServer.java)、[管理端脚本](../../yuweiju-web-vue/yuweiju-admin/package.json)、[小程序客服页](../../yuweiju-weixin-miniapp/pages/customer-service/index.js)。
- [统一接口计划](./ExecPlan%20-%20统一三端接口与环境配置.md)：HTTP/WebSocket authScope、ticket 事件和运行时契约测试。
- [后端边界计划](./ExecPlan%20-%20拆分后端业务边界.md)：WebSocket transport adapter 归属必须在本计划的分布式改造后执行。
