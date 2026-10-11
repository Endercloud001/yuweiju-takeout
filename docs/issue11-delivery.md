# Issue #11 人工验收与交付

2026-10-12，维护者反馈“全部验收通过”，并要求停止本项目所有前后端服务，使用 yuweiju-deliver 完成交付。对应人工验收候选为 `5e94e10ca2858086fea145a733386b268267653f`；此前已授权必要提交、推送、PR、合并、同步和关闭，发布目标为 GitHub main，无生产部署或 Release。

## 结果与范围

模拟支付重试不会把已履约订单恢复到待接单；Service 决定转换与归属，具名 Mapper 使用精确旧状态条件持久化，竞争失败返回业务失败。接单、拒单、用户/管理员取消、派送、完成维持原合法状态集合。复用 markPaid、催单、再来一单保留校验与真实代理事务，核心写入失败回滚。统计复用具名查询。

后端改变业务职责与条件更新，管理端及小程序源码无需修改；公开 API/schema/字段/code、认证、金额、历史图片与风险读取保持兼容。当前没有未付款超时/配送自动完成的后端任务，支付/催单没有 WebSocket 通知发送 hook，未新增功能或把缺失项虚报通过。独立 DELETE 购物车与 POST 再来一单不是跨请求事务。

遵循 [后端规范](standards/backend.md)、[workflow](agents/workflow.md)、[ADR 0001](adr/0001-retain-conventional-backend-layering.md)。具体方案与业务矩阵见 [实施方案](issue11-plan.md) 和 [验证记录](issue11-verification.md)。

## 精确版本证据

统一 verifier 在新 tracked snapshot 对 `5e94e10` 执行五组检查，全部 exit 0、stopped=true，无认证挂载与模型调用：后端 package/85 测试；管理端 lint/typecheck/build/11 测试；真实 Spring/MyBatis/MySQL/HTTP 366 个具名断言；文档链接；小程序消费者 31 测试。测试包含合法/拒绝/缺单、用户归属与管理员权限、重复支付、两个真实竞争场景、状态写失败以及购物车删除后第二条插入失败的真实代理回滚；最后任务夹具/触发器/Redis 清理与共享数据保留通过。

实际本机三端链：微信工具原生 details→pay→确认模拟支付→success，管理端浏览器实际接单→派送→完成，小程序本人详情读取 status5/pay1/amount19；使用新合成订单 911201，未替换 HTTP 业务响应。维护者随后确认全部人工项通过。仅证明隔离模拟流程，未证明真实支付退款、正式微信认证、旧订单 969/974 或生产发布。

本地完整原始日志/截图在项目 `.scratch/issue11-afk-20261011/` 的 coding、continuation、independent、live 目录，不自动作为远端附件或 fresh clone 材料。历史六份证据已清理，按维护者决定用新隔离样本替代，未重跑原库写入。规范与需求独立审查各 0 项可操作发现。

本次交付仅增加本验收记录，源码、探针、两份任务文档与已验收候选相同；复用其精确版本检查，不机械重跑业务写入。最终记录文档使用统一 verifier 检查链接与 shell 语法，另核对 diff/消费者兼容。

## 服务与回退

本项目运行中的五个隔离服务已按具名 Compose 停止，MySQL/Redis 合成卷与证据保留；Windows 无项目 Java/Node/Python 服务，WSL 无 Java/Vite/HTTP 服务，Docker 无运行中的项目容器。后端/网关 18111、管理端 18114 已释放；本票微信项目关闭，自动化 9422 已释放。没有退出 Docker/WSL、清卷或修改其他应用服务。

交付工作树位于项目 `.scratch/issue11-delivery-20261012`，主工作区当前分支与未提交变更保留。回退使用审查后的反向代码提交，无 schema 迁移；代码回退不逆转已完成订单或 SQL 外副作用。恢复验收服务须核对精确候选、端口和合成资源，不重新启动已结束 AFK supervisor。
