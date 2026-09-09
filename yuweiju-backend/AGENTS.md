# Yuweiju 后端协作规则（AGENTS.md）

## 目录与模块
- `yuweiju-backend/src/main/java/com/yuweiju/annotation`：自定义注解，如 `@AutoFill`。
- `.../config`：Spring Boot 配置项，包括 `WebMvcConfiguration`、`RedisConfiguration` 等。
- `.../controller/{admin,user,notify}`：控制器层按角色分区，admin/user 负责管理/用户接口，notify 负责消息回调。
- `.../service` + `service.impl`：业务逻辑接口与实现，按语义划分。
- `.../mapper`：MyBatis 映射，XML/注解并行。
- `.../task`、`.../websocket`、`.../exception`、`.../result`、`.../utils` 等提供定时、异常、统一返回、工具类。
- `yuweiju-pojo`、`yuweiju-common`、`yuweiju-server` 三个子模块分别含 DTO/VO/entity、共享常量/工具、核心服务逻辑，开发时优先明确各模块边界。

## 代码规范摘录
- **命名**：类/接口用 UpperCamelCase，方法/参数用 lowerCamelCase，常量用全部大写加 `_`。
- **数据表字段/JSON**：采用 snake_case，与 MyBatis 映射保持一致。
- **Service/Controller 命名**：
  - Service 接口+实现：`OrderService` + `OrderServiceImpl`。
  - Controller 包含字符表示上下文，例 `admin/OrderController`。
- **日志/异常**：务必用 `@Slf4j` + `log`，不要用 `System.out.println`。捕获异常时保留 stack trace，抛出时 `throw new RuntimeException("...", e)` 并记录 `BaseException` 继承体系。
- **实体/DTO/VO 注释**：类/字段/方法需提供 Javadoc 或行内注释，说明含义、范围和返回值。TODO/FIXME 必须带模块、目的和日期。
- **集合/流规范**：避免 `forEach` 中修改集合，遵循 `Map.entrySet()` 而不是 `keySet()`+`get()`，使用 `Collections.emptyList()` 避免 null。
- **线程/资源**：慎用原始 `ThreadLocal`，修改后记得 `remove()`；使用 `StringBuilder` 拼接大量字符串。
- **JDBC/MyBatis**：Mapper 方法参数超过 1 个时必须用 `@Param`；复杂 SQL 推荐 XML mapper，避免在注解中写长 SQL。
- **定时任务/配置**：`@Scheduled` 任务使用 cron，配置类加 `@ConfigurationProperties` 并加 `@Validated`，缺失条目需明确错误提示。

## COBOL 现代化补充
- 每个 ExecPlan/PR 需说明正在替换或对接的 COBOL 流程/数据源，以及如何验证（表、API、文档）。
- 若涉及 COBOL 数据同步/导出，在 plan 里写明同步点、回退步骤、测试数据对比。
- 接口变更需列明对 `yuweiju-web-vue` 与小程序的影响，接口文档更新写在 `yuweiju-document/api`。

## 必读参考
- `yuweiju-backend/.trae/rules/rules.md`：Java 命名、注释、日志、异常、Mapper/Entity 规范的权威说明。
- `yuweiju-document/api` + `yuweiju-document/db`：接口、数据库设计文档。
- `yuweiju-document/execplans/余味居 外卖项目 To-do List.md`：当前需求与待办优先级。
- 若触及管理端/小程序，还需检查其 `.trae` 规则与 AGENTS。

## 做事流程
1. 读完相关 ExecPlan，确认 OBS（observable outputs）和 COBOL 依赖。
2. 先研究接口/数据库文档，写 ExecPlan，记录发现、决策和验证步骤。
3. 编码、测试、文档同步完成后再更新 ExecPlan 进度、记录 Surprises & Decisions。
4. PR 中附上遵循 `rules.md` 条目的说明（例如命名/日志差异），并标出 COBOL 现代化凭据。

## Guardrails
- 不随意改接口字段或数据库 schema，除非 Plan 中列出迁移/回退路径。
- TODO/FIXME 必须注明模块、目的、关联的 `issue`/`plan`。
- 日志必须用 `log` 记录，不打印敏感信息（密码、token）。
