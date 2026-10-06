# 统一三端接口与环境配置 实现计划

> 2026-10-06 维护更新：本文保留当时方案/调查/验证事实，不作为现行强制规则。PLANS 已退出流程；当前协作与技术规范见仓库 `docs/agents/workflow.md`、`docs/standards/`。旧工具规则和冲突 Code Style 已退役；历史来源名称不再代表执行要求。


> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:executing-plans`（或 `superpowers:subagent-driven-development`）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 让 Spring Boot 后端、Vue 管理端和微信小程序共享一份可验证的 API 契约、统一的环境配置和错误处理方式，同时保持现有 `{ code, msg, data }` 响应结构与 Token 字段兼容。

**架构：** 后端接口文档和数据库文档是业务真相源，在其上维护 OpenAPI 3 描述；管理端继续使用 Axios + TypeScript 类型，小程序增加轻量 `request` 封装，三端通过契约测试检查路径、方法、字段和鉴权。环境地址只从启动参数或构建环境读取，页面不再硬编码 `localhost`。

**技术栈：** OpenAPI 3、Spring Boot、Jackson/Bean Validation、Axios、Vue 3 + TypeScript、微信小程序 `wx.request`/`wx.connectSocket`、OpenAPI Generator 或 `openapi-typescript`、JSON Schema/契约测试。

---

## Purpose / Big Picture

当前管理端已有 `src/api/http.ts`，但小程序的 AI 助手和人工客服页面各自声明 `const baseUrl = 'http://localhost:8080'` 并重复读取 Token；WebSocket、HTTP 和两个客户端的 Token 约定也分散在页面代码中。完成后，开发者通过一个环境变量即可切换本地、测试和生产；新增接口只需更新 OpenAPI 和对应模块；非法响应、过期登录、网络超时和 Blob 下载在三端有一致行为。静态门禁只负责快速反馈，运行时契约测试负责验证真实响应。

本计划遵守 `docs/standards/backend.md` 的 `ApiResult`、REST、参数校验要求，遵守 `docs/standards/admin.md` 的 `VITE_API_BASE`、`token`、严格 TypeScript 要求，并遵守小程序 `AGENTS.md` 的字段一致性要求。接口和数据库变更必须同时核对 `yuweiju-document/api/`、`yuweiju-document/db/` 和 To-do List。

## Progress

- [ ] 从现有 HTML 接口文档、Controller、前端 API 模块和小程序页面提取契约。
- [ ] 建立 OpenAPI 源文件、生成/维护类型和契约校验脚本。
- [ ] 统一管理端与小程序 HTTP/WebSocket 配置、Token、错误和超时处理。
- [ ] 完成三端构建、接口烟测和兼容性验收。

## Surprises & Discoveries

- 管理端约定管理员 Token 为 `token`，小程序用户端约定为 `authentication`；两者不能在统一时误改为同一个字段。
- 管理端 `http.ts` 的超时为 600000ms，AI/导出等慢请求与普通 CRUD 共用一个超时；统一客户端时应按请求类型设置上限。
- 小程序源码包含编译生成的 `common/vendor.js` 和 `node-modules`，契约改造应只触及页面源码和新增公共封装，不能编辑生成依赖。

## Decision Log

- **Decision：** 先做“契约源 + 轻量客户端封装”，暂不生成覆盖小程序全部页面的庞大 SDK。**Rationale：** 现有小程序是编译产物结构，增量迁移比整体替换风险低。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** 保留管理员 `token` 和用户 `authentication` 两个鉴权头。**Rationale：** 后端 JWT 配置明确区分管理端和用户端，兼容性优先。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** OpenAPI 只描述 HTTP；WebSocket 事件另以 JSON Schema 和事件表描述。**Rationale：** OpenAPI 对 WebSocket 事件语义不足，分开描述更准确。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** `contract-check.mjs` 只被定义为最小静态门禁，不被当作真实契约证明；核心路径另用 Schemathesis 对运行中的后端发请求，Prism 只用于没有后端时的 mock/消费者开发。**Rationale：** 静态字符串匹配可能出现文档和实现同时遗漏却仍然通过的假阳性。**Date/Author：** 2026-08-08 / Codex。
- **Decision：** HTTP `authScope` 与 WebSocket handshake 共用同一套身份解析语义；WebSocket ticket、旧 query token 兼容窗口、admin/user scope 和权限校验由可靠性计划先实现，接口计划只消费其稳定事件契约。**Rationale：** 避免两份计划分别改握手代码造成鉴权分叉。**Date/Author：** 2026-08-08 / Codex。

## Outcomes & Retrospective

验收结果应能证明：同一个接口在 HTML 文档、OpenAPI、Java Controller、Vue API module 和小程序 wrapper 中的路径、方法、请求字段、响应字段一致；切换 `VITE_API_BASE` 或小程序运行环境配置不会修改业务代码；401/403、业务失败、网络失败和文件下载行为可预测。

## Context and Orientation

HTTP 后端入口在 `yuweiju-backend/src/main/java/com/codeying/controller/admin` 和 `controller/user`，统一结果为 `ApiResult`；管理端 API 在 `yuweiju-web-vue/yuweiju-admin/src/api/`，类型在 `src/types/`；路由和 Token 守卫在 `src/router/index.ts` 与 `src/stores/user.ts`；小程序核心页面在 `pages/ai-assistant`、`pages/customer-service`、`pages/order`、`pages/pay`，编译入口是 `app.js`。当前接口 HTML 位于 `yuweiju-document/api/`，数据库设计和初始化脚本位于 `yuweiju-document/db/`。

## Plan of Work

### 任务 1：冻结现有契约并生成差异报告

**文件：**

- 读取：`yuweiju-document/api/余味居-管理端接口.html`
- 读取：`yuweiju-document/api/余味居-用户端接口.html`
- 读取：`yuweiju-document/db/数据库设计文档.md`
- 读取：`yuweiju-document/db/db_init.sql`
- 创建：`yuweiju-document/api/openapi.yaml`
- 创建：`yuweiju-document/api/websocket-events.md`

- [ ] 以 Controller 和现有 HTML 为准登记所有管理端、用户端路径、方法、鉴权头、请求 DTO 和 `ApiResult` 响应。
- [ ] 为分页、登录、订单、客服、AI、图片上传/下载写出实际 schema；字段沿用现有 snake_case/camelCase 约定，不在契约文件中擅自重命名。
- [ ] 为客服 WebSocket 登记连接 URL、`clientType`、`sessionId`、消息 type、错误消息和重连行为。
- [ ] 在 HTTP schema 与 `websocket-events.md` 中同时登记 `authScope`、Token/ticket 来源、权限失败码和连接恢复规则；具体握手实现依赖[可靠性计划](./ExecPlan%20-%20补齐生产级可靠性与质量门禁.md)。
- [ ] 用 Spectral 或 OpenAPI CLI 执行格式检查，并输出当前 Controller/HTML/OpenAPI 的差异清单。

### 任务 2：建立环境配置入口

**文件：**

- 修改：`yuweiju-web-vue/yuweiju-admin/src/api/http.ts`
- 修改：`yuweiju-web-vue/yuweiju-admin/vite.config.ts`
- 创建：`yuweiju-web-vue/yuweiju-admin/.env.example`
- 创建：`yuweiju-weixin-miniapp/common/config.js`
- 创建：`yuweiju-weixin-miniapp/common/http.js`
- 修改：`yuweiju-weixin-miniapp/pages/ai-assistant/index.js`
- 修改：`yuweiju-weixin-miniapp/pages/customer-service/index.js`

- [ ] 管理端将 `VITE_API_BASE` 缺失时的默认值限制为开发环境 `/api`；生产构建如果没有明确值则失败。
- [ ] 小程序通过 `wx.getAccountInfoSync()`、开发者工具环境配置或构建注入选择 API Base URL，禁止页面写死 `localhost`；生产必须是 HTTPS，WebSocket 必须是 WSS。
- [ ] `common/http.js` 暴露 `request({ path, method, data, authScope })`，根据 `authScope: 'admin' | 'user'` 自动选择 `token` 或 `authentication`，统一处理 HTTP 错误、`code !== 1`、超时和登录失效。
- [ ] 客服 WebSocket 只从 `common/config.js` 拼接 URL；连接、关闭、重连和心跳事件不再散落在页面。

### 任务 3：统一管理端类型和错误边界

**文件：**

- 修改：`yuweiju-web-vue/yuweiju-admin/src/types/api.ts`
- 修改：`yuweiju-web-vue/yuweiju-admin/src/api/http.ts`
- 修改：`yuweiju-web-vue/yuweiju-admin/src/api/modules/*.ts`
- 修改：`yuweiju-web-vue/yuweiju-admin/src/router/index.ts`

- [ ] 定义 `ApiResponse<T>`, `PageResponse<T>`, `ApiError` 和 `AuthScope`，禁止用 `any`；对 JSON 扩展字段使用 `unknown` 加类型守卫。
- [ ] 响应拦截器区分 401/403、网络失败、业务失败和 Blob；401 清除 Cookie、Pinia 状态并跳转登录，不能让每个页面自行处理。
- [ ] 为导出、上传、AI 调用设置明确的 per-request timeout 和 AbortController 取消策略；普通 CRUD 不再共用 10 分钟超时。
- [ ] 让所有 API module 的路径和返回类型与 OpenAPI 对照，删除页面中直接调用 Axios 的代码。
- [ ] 为小程序 `common/http.js` 定义 JSDoc 类型或独立 `.d.ts`，并加入轻量运行时 schema guard；至少校验登录、下单和 AI session 的 `code/msg/data`、ID、状态和必需字段，不能只依赖 JavaScript 调用方自觉。

### 任务 4：迁移小程序核心页面

- [ ] 先为 `ai-assistant`、`customer-service`、`order`、`pay` 写 wrapper 单元测试或可运行的请求桩，覆盖成功、业务失败、超时和 Token 失效。
- [ ] 将这些页面的 `wx.request` 替换为 `common/http.js`，保留现有字段名和用户可见流程。
- [ ] 将客服 WebSocket 消息解析成白名单事件类型；未知事件只记录脱敏诊断信息，不直接渲染任意字段。
- [ ] 为 `common/http.js` 的登录、下单、AI session 和客服消息增加 schema 校验测试；优先使用轻量手写 guard，若 schema 数量增长再引入 Ajv，避免把完整 JSON Schema 校验器注入所有小程序页面。
- [ ] 按小程序开发者工具编译规则执行编译和真实测试设备回归；不要修改 `common/vendor.js`、`node-modules` 或其他生成文件。

### 任务 5：加入契约与烟测门禁

**文件：**

- 创建：`yuweiju-document/api/contract-check.mjs`
- 修改：`yuweiju-backend/scripts/api_smoke.ps1`
- 创建：`.github/workflows/api-contract.yml`

- [ ] 运行 `contract-check.mjs` 作为最小静态门禁：验证 OpenAPI 可解析、路径唯一、响应统一、Token scope 已声明、关键路径已登记；报告中明确它不能证明运行时响应正确。
- [ ] 在启动的 Spring Boot 测试服务或 Prism mock 上运行 Schemathesis `st run yuweiju-document/api/openapi.yaml --url http://127.0.0.1:8080` 的 examples/coverage/fuzzing 阶段；为登录 Token、数据库测试数据和非幂等接口配置安全 fixtures，禁止对生产环境运行 fuzzing。
- [ ] 设定运行时契约门禁：所有核心操作的响应状态码、`ApiResult` schema、错误结构和鉴权拒绝行为通过；若暂时无法容器化后端，CI 只能标记为“静态门禁已通过，运行时契约待执行”，不能报告全量契约通过。
- [ ] 后端烟测覆盖管理员登录、用户登录、菜品列表、购物车、下单、AI session、客服 session；测试数据使用沙盒数据库和无效外部凭据。
- [ ] CI 依次运行 OpenAPI lint、Maven 测试、管理端 lint/typecheck/test、契约脚本；小程序执行 Node 静态契约检查并由开发者工具完成编译门禁。

## Concrete Steps

工作目录为 `E:\Learning Files\yuweiju-takeout`：

    npm --prefix yuweiju-web-vue/yuweiju-admin run lint
    npm --prefix yuweiju-web-vue/yuweiju-admin run typecheck
    npm --prefix yuweiju-web-vue/yuweiju-admin run test
    mvn -q -f yuweiju-backend/pom.xml test
    node yuweiju-document/api/contract-check.mjs
    powershell -ExecutionPolicy Bypass -File yuweiju-backend/scripts/api_smoke.ps1

## Validation and Acceptance

在三种环境配置下，管理端和小程序均能访问后端且不含硬编码地址；所有核心接口契约检查通过；错误响应统一；管理员和用户 Token 不串用；客服使用 WSS/WS 正确连接；API smoke test 通过；删除一个环境变量时构建或启动会清楚失败；数据库和 API 文档字段无未记录变化。

## Idempotence and Recovery

OpenAPI 提取和契约检查可重复运行。页面迁移采用 wrapper 逐页切换，可通过保留旧调用的短期分支回滚，但不得恢复硬编码生产地址。接口路径或字段必须先在 OpenAPI、后端 DTO、两端类型和烟测中同步修改，再部署；若线上兼容性失败，优先回滚客户端 bundle，后端保留旧路径一段兼容窗口。

## Artifacts and Notes

计划完成后应有 OpenAPI 源文件、WebSocket 事件文档、管理端 `.env.example`、小程序公共配置/请求封装、契约检查脚本和 CI 工作流。生成客户端或文档必须注明生成命令和源文件，不能手工修改生成结果后却不更新源契约。

## Interfaces and Dependencies

- [Spring Boot API result and project rules](../../docs/standards/backend.md)
- [Management API](./../api/余味居-管理端接口.html)
- [User API](./../api/余味居-用户端接口.html)
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)
- [OpenAPI Generator](https://github.com/OpenAPITools/openapi-generator)
- [Schemathesis](https://schemathesis.readthedocs.io/en/stable/)：根据 OpenAPI 对真实/测试服务器执行 property-based runtime contract tests。
- [Prism](https://github.com/stoplightio/prism)：从 OpenAPI 提供 mock server，适合消费者开发，不替代真实服务契约测试。
- [sky-take-out](https://github.com/shuhongfan/sky-take-out)：外卖管理端、小程序和后端闭环的业务参考；只复用成熟边界和测试思路，不复制其凭据或业务代码。
- [可靠性计划](./ExecPlan%20-%20补齐生产级可靠性与质量门禁.md)：WebSocket ticket、握手 authScope、Redis 协调和重连实现的前置依赖。
