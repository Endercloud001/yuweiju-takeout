---
name: yuweiju-java-backend
description: >
  Java 后端开发规范与重构助手。只要用户提到重构后端、瘦身 Controller、抽取 DTO/VO/Query、统一分页与返回、
  强化 Service 事务边界、减少重复代码、规范包结构/命名、对标 sky-take-out 或阿里巴巴 Java 开发手册，
  创建/修改后端接口、梳理订单/菜品/套餐/报表等链路时，务必使用本 Skill。
  在进行回归或质量验证（mvn 构建与 scripts/api_smoke.ps1 冒烟）时也要使用本 Skill。
---

# 目标
- 将控制器从“业务+模型定义”转为“薄控制器 + 强 Service”
- 将内嵌 DTO/VO/PageData 抽到标准包结构，命名统一
- 提供分页与对象装配的统一做法，逐步消除重复代码
- 保持向后兼容：不改变既有接口路径与入参/出参结构（除非走版本化）

# 包结构与职责
- com.codeying.controller.[admin|user]：只做入参校验、调用 Service、返回 VO
- com.codeying.dto.[admin|user].<domain>：DTO/Query（请求参数）
- com.codeying.vo.[admin|user].<domain>：VO（响应结构）
- com.codeying.common.page：PageData 等通用结构
- com.codeying.service/impl：聚合核心业务逻辑，@Transactional，调用多个 Mapper
- com.codeying.mapper：基础 CRUD 与必要查询（复杂 SQL 放 XML）
- com.codeying.assembler/convert：对象转换（DTO→Entity、Entity→VO），初期可用 Assembler，后续可选 MapStruct
- com.codeying.utils/constant/properties/interceptor/config：工具与配置

# 统一约定
- 命名：DTO=请求体，Query=查询参数，VO=返回体，Entity=数据库映射
- 校验：Controller 入参使用 @Valid；DTO 字段声明 javax.validation 约束
- 文档：对外方法、DTO/VO 字段与 Entity 字段均有 Javadoc/注释
- 返回：统一 ApiResult<T>；分页统一 PageData<T>
- 日志/异常：INFO 打点关键流程，ERROR 打完整栈；自定义业务异常 + 全局异常处理

# 工作流（每次使用本 Skill 时遵循）
1. 辨识对象：找出 Controller 内部的 DTO/VO/PageData 与重复逻辑
2. 设计外移：在 dto/vo/common 包创建对应类，维持 JSON 字段不变
3. 代码迁移：Controller 引用外移后的类；必要时新建 Assembler 减少拼装重复
4. 业务下沉：将业务组装与事务控制迁入 Service，Controller 仅做参数与转发
5. 验证构建与冒烟：`mvn -DskipTests clean package` + `powershell -File scripts/api_smoke.ps1`
6. 生成“变更摘要 + 受影响接口 + 风险点 + 回滚说明”

# 模板（示例）
## PageData
放置于 `com.codeying.common.page.PageData<T>`，字段：`total`、`records`

## Assembler（可选）
```
public final class OrderAssembler {
  public static OrderVO toUserVO(Orders o, List<OrderDetail> ds) { ... }
}
```

## Controller 约定
- 只做入参校验（@Valid）、日志（脱敏）、调用 Service、返回 ApiResult<VO> / ApiResult<PageData<VO>>
- 禁止在 Controller 内声明 DTO/VO/PageData 内部类

# 质量与安全基线
- 不打印密码/密钥等敏感字段
- 统一异常返回；第三方 API 失败记录入参与响应摘要
- Redis 或第三方异常有降级策略（不影响关键链路稳定性）

# 回归与评估
- 构建：`mvn -DskipTests clean package`
- 冒烟：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/api_smoke.ps1`
- 评审清单：
  - Controller 是否仅做入参与转发
  - DTO/VO/Query 是否按职责拆分、命名统一
  - Service 是否承载业务与事务边界
  - 分页与对象装配是否统一
  - 公共注释是否齐全

