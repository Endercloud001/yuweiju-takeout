# Issue #11 实施方案

本轮根目录与 Git root 为 `/home/agent/workspace`，挂载输入只读；新的隔离样本授权覆盖缺失的六份历史材料。#2 CLOSED，起点 86e866a 已包含 #13/#14/#9/#12 及 runtime 改进。首轮历史截止时间 2026-10-11T14:01:16.063Z 已失效。本次续跑为第二且最后编码轮，当前 supervisor deadline 为 2026-10-11T15:37:46.933Z；首轮已用538765ms，剩余3061235ms，累计六十分钟和总共两轮上限不变。独立验证由 host 另用二十分钟。恢复起点2c6c8fb是未验证WIP，不是完成候选。不得重启 supervisor、使用并行代理、发布或写原库。

依据 [workflow](agents/workflow.md)、[后端规范](standards/backend.md)、[管理端规范](standards/admin.md)、[小程序规范](standards/miniapp.md)、[ADR0001](adr/0001-retain-conventional-backend-layering.md)、[ADR0002](adr/0002-preserve-historical-order-images.md) 与 [术语](../GLOSSARY.md)。

## 当前事实与文件

`UserOrderController` 与 `AdminOrderController` 已委派 `OrdersApplicationService`。Impl 中支付与各履约写入使用 updateById，读取后写入存在并发覆盖且支付无状态检查。confirm/reject 仅状态2，deliver仅3，complete仅4，用户取消1/2，管理员取消1/2/3。取消/拒绝只写原因与取消时间，不写退款或 payStatus；完成写 deliveryTime。repetition 已有代理事务但需检查批量写入结果；通过存储明细还原金额/图片，清理自己购物车。reminder 目前只有 Redis 60 秒限流，Service 无归属/状态检查；实际两个页面只在状态2提供催单。因此 reusable Service 催单限定自己状态2订单，再执行现有可降级限流。

将修改 `yuweiju-backend/src/main/java/com/codeying/service/impl/OrdersApplicationServiceImpl.java`、`service/OrdersApplicationService.java`、`mapper/OrdersMapper.java` 与必要的 Controller 异常处理。保留既有层次，不拆读服务，不改其他查询；统计复用已存在 countByStatus。新增普通 Service 行为测试、`.sandcastle/environment/Issue11Probe.java`、`issue11-check.sh` 与 [验证记录](issue11-verification.md)。

## 状态与事务

每条写入具名 Mapper 使用主键+精确旧 status；用户支付/取消还带 user_id。影响行数必须为1，竞争失败抛业务异常。支付仅1→2，同时 PAID、checkoutTime；已 PAID 的2/3/4/5重复支付返回既有 mock VO，不重复写时间/评分；CANCELLED及不一致状态拒绝。markPaid 自身校验 usable ID、缺单与状态；HTTP paymentMock 自身事务包住私有共享支付步骤，自调用不依赖新代理。

所有转换、repetition 的 SQL 事务在既有公开 Service 代理边界。repetition 使用 owned named read、存储详情与 clearForUser、检查 saveBatch；任一核心 SQL 异常传播并回滚。保留 reason/时间/pay/amount/image 与已有状态拒绝消息；ID必须正数，admin原因必须非空且 trim。付款附加评分保留可选降级；不改风险算法。非SQL通知/Redis不能被 SQL rollback 撤回。

## 入口、三端兼容与审计

公开 API/schema/JSON/code、token/authentication 与身份不变；控制器只协议与身份委派。管理员和小程序现有调用路径及 DTO 可继续使用，前端无需修改，仍运行管理员四项检查及 consumer 回归。小程序原生编译与人工体验由 host 提供，历史969/974不作为本轮证据。

审计实际全仓调用点：原始markPaid仅paymentMock自调用；最终paymentMock与markPaid各自通过公开代理事务进入共享私有支付步骤，生产代码没有其他markPaid调用者，其他转换由两控制器进入；AI催单仅文字指引。当前无未付款超时/自动配送完成任务。OrderRiskTask只委派风险训练且有开关，保持原实现。WebSocketServer onMessage为空、sendToAllClient只传输，没有履约写入；支付/催单当前没有发送通知调用，不新增通知功能。

## 步骤、检查与回退

1. 先保存本方案，落实条件更新与复用Service验证、公共API说明。
2. 普通测试覆盖参数、所有转换、重试、竞争行数与评分降级。
3. 新隔离 MySQL/Redis+Spring HTTP probe，DATABASE确认后才写911001起的新样本，legal/wrong/missing、权限、重复/并发、完整支付至完成与两端读取、购物车第二写失败和状态写失败、代理事务/评分开关及WebSocket不写证据；finally删除任务行/触发器/Redis并断言其他数据未变。
4. 从根运行 exact Maven package、管理员 npm ci/lint/typecheck/test/build、实际jar classpath提取和probe、两份文档链接检查；完整日志写 `.scratch/issue11`，失败保留并按检查批次记载。
5. 检查closing.json、scope/diff，explicit task-file staging与普通本地commit。候选交host独立与人工验收；不push/PR/merge/close。

回退使用审查后的普通反向代码提交，禁止覆盖用户修改或强制Git操作。probe只清理已确认空闲且本次创建的fixture资源；不清表，不改schema，不还原历史已完成订单。任何Git安全拒绝记录category/reason/next step，不旁路。
