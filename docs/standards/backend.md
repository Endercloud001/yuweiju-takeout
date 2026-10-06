# 后端技术规范

适用于后端 Java、测试、配置映射、HTTP/任务/WebSocket 入口与持久化代码。依据 [ADR 0001](../adr/0001-retain-conventional-backend-layering.md)、[issue #1](https://github.com/Endercloud001/yuweiju-takeout/issues/1)、[issue #2](https://github.com/Endercloud001/yuweiju-takeout/issues/2) 的已确认职责决定及 [规范研究](../yuweiju-backend-standards-research.md)；2026-10-06 维护者授权协调规则。规范是后续实施目标，不宣称全部代码已符合。

## 当前结构与兼容性

后端为单 Maven 工程，源码 `com.codeying`，当前 Java 21、Spring Boot 3.5.0、MyBatis Plus 3.5.7；版本以 POM 为准。沿用 Controller/Service/Mapper 常规分层和 Service 接口/Impl。已有 ApplicationService 按真实职责整理，有复用或事务语义可保留；合理薄类及 BaseMapper 继承不算缺陷，不以行数/类数验收。

保留公开路径、字段、类型、业务 code、分页/排序/时间口径、认证、schema、原数据与 ID。Java/JSON 沿用实际字段，不把数据库 snake_case 推广为 JSON 重命名要求。使用现有 `ApiResult<T>`，包含 `code/success/message/msg/data`；`PageData<T>` 为 `total/records`，分页沿用 MP。必要调整先列消费者和兼容/回退方案。

## 职责与查询

- Controller、任务和 WebSocket 处理协议、身份上下文、参数及响应；业务规则和事务委派 Service，Controller 不直接访问 Mapper。
- Service 承担业务不变量、金额、状态流转、归属/可售校验及用例编排；其他入口复用时规则仍成立。DTO/Query 表达请求，VO 表达返回，Entity 表达持久化；转换不覆盖客户端无权控制的字段。
- **全部业务筛选、统计及条件持久化走具名 Mapper 方法**。简单条件可在 Mapper 内复用 Wrapper；标准主键 CRUD 保留 BaseMapper。复杂动态 SQL、聚合、Join、子查询放 XML，参数绑定且结果类型明确；多参数显式 `@Param`，不拼接用户值形成 SQL。不要求每个 Mapper 新建 XML。
- 算法、模型调用、缓存、HTTP 和文件按真实职责划分；Mapper 不承载业务决策，纯计算不混入协议/IO。不机械新增第四层、接口工厂或模块目录。

## 校验、事务与副作用

请求使用 `jakarta.validation`，入口明确触发 `@Valid`/适用校验；业务校验在 Service。金额沿用 BigDecimal，时间边界、时区、分页排序及空结果口径兼容。

多表事务在 Service 用例边界表达；检查 Spring 代理调用与异常传播，自调用新增 `@Transactional` 不证明事务生效。验证核心写入失败时订单/明细/购物车原子性；SQL 回滚不覆盖 Redis、模型文件或 HTTP。核心业务失败与权限拒绝不能吞掉；非关键推荐统计、风控附加读取失败按已确认用例降级，如实表示不可用。

查询保持读取语义。[历史订单图片](../adr/0002-preserve-historical-order-images.md) 保留已有明细图片，缺失兜底，不查询时用商品现图覆盖旧记录。训练/预测区分实际完成、跳过、失败及产出，不以触发或旧模型冒充本次成功。

## 写作、依赖与资源

UTF-8、4 空格，类 UpperCamelCase、成员 lowerCamelCase、常量 UPPER_SNAKE_CASE；保留既有命名，不因布尔前缀建议改变序列化。公共 Service/Mapper API 说明业务、参数、结果及失败条件，内部注释解释原因；覆写不重复接口正文。

新增/修改依赖注入优先构造器。流、文件和客户端按生命周期关闭，线程上下文使用后清理，异步任务检查线程安全及取消/失败。配置沿现有属性类管理和必要字段校验，不散落个人路径或凭据，不添加无用途依赖。

## 异常、日志与认证

沿用业务异常和全局处理，保留 cause；对外友好错误并隐藏堆栈、数据库及凭据细节。使用现有 Logger/Slf4j 和占位符，关键节点保留可定位上下文，避免重复异常日志。不记录密码、token、密钥、完整敏感对话或未脱敏第三方正文。

HTTP/WebSocket、用户/管理员 scope、登录归属、模拟支付和上传继续保留安全措施。搬层逐项核对权限未丢失。真实支付、证书轮换和正式发布按专项授权及安全方案核验，历史设想不当作已实现能力。

## 验证与评审例子

工作目录 `yuweiju-backend/`，按改动执行 `mvn test` 或受影响测试，构建执行 `mvn package`。检查是否经真实 SQL/Spring 代理，Mockito 不能证明数据库事务。普通测试围绕金额、权限、查询/分页、空数据、事务失败及依赖降级，不锁定私有方法、行数或 Wrapper 字符串。

`scripts/api_smoke.ps1` 会 POST 分类/菜品，先查环境及写入授权。现有 `docs/verification-evidence/2026-10-05/` 脚本含原库、训练和文件副作用，复用前隔离；原始输出已清理。

- **订单查询**：入口取得管理员身份并校验参数，Service 确认规则，具名 Mapper 承载筛选/分页；附加风险不可用仍返回合法订单与真实状态。验证越权、空页、排序/时间边界，读取不修改历史图。
- **下单事务**：入口接收 DTO，Service 校验地址归属、可售并算可信金额，经代理事务调用具名查询及订单/明细/购物车写入；隔离测试注入核心失败验证回滚，另查 Redis/文件副作用。
