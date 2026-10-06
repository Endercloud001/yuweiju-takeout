# 后端统一规范的一手源码参考

调查日期：2026-10-05。用途：为首期覆盖全部后端业务的职责恢复提供候选规范；不是实施授权，也不是已采纳的新架构规则。保留 Spring Boot、MyBatis Plus、MySQL、常规分层、原库数据及两前端外观。

## 参考项目实际做法

选择 mall 的电商商品管理与 RuoYi-Vue 的管理业务，是因为它们有可追踪的 Spring Controller → Service → MyBatis 数据访问链；星数、流行程度不构成规范正确性的证据。未复制它们的业务、认证或数据库设计。

| 参考链 | 实际源码事实 | 对余味居的启发 |
| --- | --- | --- |
| mall 商品管理 | [PmsProductController](https://github.com/macrozheng/mall/blob/master/mall-admin/src/main/java/com/macro/mall/controller/PmsProductController.java) 将创建、修改、查询委派 Service；[PmsProductServiceImpl](https://github.com/macrozheng/mall/blob/master/mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java) 组织关联数据写入，并在简单列表查询中构造 Example 条件。 | 薄 Controller 是正常做法；Service 可以承担业务编排，也可以构造简单查询条件。不能用三层代码量均衡验收。 |
| mall 自定义数据访问 | [PmsProductDao](https://github.com/macrozheng/mall/blob/master/mall-admin/src/main/java/com/macro/mall/dao/PmsProductDao.java) 定义 `getUpdateInfo`；[XML](https://github.com/macrozheng/mall/blob/master/mall-admin/src/main/resources/dao/PmsProductDao.xml) 完成关联查询与结果映射。 | 复杂查询有具名入口及独立 SQL；余味居可沿用 Mapper 命名，无需额外复制 Dao 层。 |
| RuoYi-Vue 用户管理 | [SysUserController](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-admin/src/main/java/com/ruoyi/web/controller/system/SysUserController.java) 保留权限入口和部分校验；[SysUserServiceImpl](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-system/src/main/java/com/ruoyi/system/service/impl/SysUserServiceImpl.java) 在 `insertUser`、`updateUser` 等事务方法组织用户、角色和岗位关系写入。 | 多表业务与事务归 Service；不能照搬参考项目留在 Controller 的业务校验。 |
| RuoYi-Vue 查询入口 | [SysUserMapper](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-system/src/main/java/com/ruoyi/system/mapper/SysUserMapper.java) 提供 `selectUserList` 等方法；[XML](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-system/src/main/resources/mapper/system/SysUserMapper.xml) 承担列表条件、关联和结果映射。 | 业务查询集中具名 Mapper 也是实际项目选择，但不证明所有项目都必须如此。 |

两者分别展示了“简单条件留 Service，复杂查询下沉”和“具名 Mapper 集中数据查询”。选哪种应由余味居的可定位性需求决定；不能宣称其中一种是行业强制要求。

## 框架语义核实

- MyBatis Plus 官方说明 `IService` 封装通用 CRUD。与当前 POM 的 3.5.7 对应的 [IService 源码](https://github.com/baomidou/mybatis-plus/blob/v3.5.7/mybatis-plus-extension/src/main/java/com/baomidou/mybatisplus/extension/service/IService.java) 中，`list(Wrapper)` 委派 `BaseMapper.selectList`，分页类似；[BaseMapper 源码](https://github.com/baomidou/mybatis-plus/blob/v3.5.7/mybatis-plus-core/src/main/java/com/baomidou/mybatisplus/core/mapper/BaseMapper.java) 已有主键、条件、列表等入口。仅继承 BaseMapper、ServiceImpl 本身不证明功能缺失，不能为增加行数重写通用 CRUD。[官方数据接口说明](https://baomidou.com/en/guides/data-interface/)
- Spring 默认代理事务拦截经过代理的外部调用；同对象内调用不会触发被调用方法的事务增强。合并 ApplicationService 与 ServiceImpl 时必须检查代理调用链和实际事务回滚，不能只搬注解。[Spring 事务注解说明](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

## 本项目规范依据（决策更新）

2026-10-06：具名 Mapper 方案 A 已由 Q6 和 issue #1/#2 接受，方案 B 废弃；当前唯一后端技术规范见 [backend](standards/backend.md)。本研究保留外部比较，不作为另一份强制规范。

## 原候选规范

以下是结合既有 [ADR 0001](adr/0001-retain-conventional-backend-layering.md)、[后端规则](standards/backend.md) 和源码事实提出的项目建议，不是从参考项目推出的行业定律。

1. **Controller**：映射请求、DTO 格式校验、调用 Service、包装响应。业务不变量、购物车数量变化、金额计算、默认地址切换及多表事务不留在 Controller；权限和用户归属检查不能因搬层丢失。
2. **Service**：承载业务规则、状态流转、用例编排及事务。数据访问交给 Mapper；不把业务规则倒灌给 Controller 或 Mapper。服务仍使用既有接口与实现命名。
3. **Mapper**：负责数据查询、写入及结果映射；复杂 Join、子查询、聚合及复杂动态 SQL 放 XML，采用具名方法、参数绑定、明确结果类型。通用 CRUD 保留 MyBatis Plus；不强制每个 Mapper 新增 XML。
4. **待选择的简单查询边界**：A，业务筛选和统计全部通过具名 Mapper 方法，简单查询可在 Mapper 内复用 MP Wrapper；B，复杂或重复查询具名下沉，局部简单 Wrapper 留 Service。A 更容易统一找查询，改动范围较大；B 较少封装，但条件仍可能散落 Service。两种都不要求重写 MP 主键 CRUD。
5. **ApplicationService / ServiceImpl**：检查每组真实职责；若只是“用例类 → 空通用 CRUD 服务 → Mapper”，优先消除没有独立语义的空转；有独立复用、事务或业务责任的服务可保留。按实际职责合并或整理，不强制新增第四层，不按类名批量删除。
6. **算法与外部 IO**：在 Service 内部按数据准备、纯计算、模型调用及运行状态拆出真实职责；外部网络、文件和缓存处理不与训练算法、HTTP 协议混成一个大类。不引入统一接口工厂、全面目录迁移或新架构框架。模型效果调优另设阶段。
7. **命名与注释**：保留项目 4 空格、接口 / Impl、DTO / VO / Entity 与既有 API 字段。公共接口 Javadoc 写清业务、参数、返回与失败条件，内部注释解释原因；不制造占位 TODO 或逐行翻译代码。Google Java Guide 可以参考，但其 2 空格缩进与本项目冲突，因此不照搬。[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html#s4.2-block-indentation)
8. **异常与日志**：沿用项目业务异常基类及全局响应，保留 cause；可恢复的智能失败必须如实展示状态，核心订单失败不得被吞掉。采用占位符和必要异常栈，避免重复日志；参数记录继续脱敏，不记录密码、令牌、密钥和完整敏感对话。Alibaba p3c 提供占位符、异常上下文与堆栈的参考，不作为引入日志框架或工具规则的理由。[p3c 日志规约](https://github.com/alibaba/p3c/blob/master/p3c-gitbook/异常日志/日志规约.md)
9. **验证**：验收金额规则、权限 / 归属、事务失败回滚、查询结果和分页、历史读路径不写库、智能异常降级及三端展示；用普通针对性测试与现有验证流程，不添加 hash、冻结 contract、baseline 或 gate。未改变行为的格式调整不单独制造镜像实现的测试。

## 规则冲突与局限

当前后端实际是 `src/main/java/com/codeying`，POM 为 Spring Boot 3.5.0、MP 3.5.7；规则仍描述 `yuweiju-server / pojo / common` 模块、`javax.validation` 和 PageHelper 示例。后续规范需要按实际目录、Jakarta 与 MP 分页校正，不为了适配旧模板拆模块或换分页库；本轮未修改规则、AGENTS、源码或 schema。

此次读取的是公开主分支代表文件及 MP 对应 tag；主分支可能变化。这些是具体职责分配的例子，不是对项目整体安全、质量、运行状态或当前受支持版本的审计。无外卖教材仓库被用作证据；无需为凑第三个参考引入来源不明的教材拷贝。
