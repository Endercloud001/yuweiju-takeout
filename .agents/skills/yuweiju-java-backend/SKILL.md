---
name: yuweiju-java-backend
description: 恢复余味居后端业务职责、定位业务查询或整理 Controller/Service/Mapper 调用链时使用。
---

# 后端职责恢复

读 [后端规范](../../../docs/standards/backend.md) 与相关 issue，按实际入口→Service→Mapper 定位规则、查询、事务及失败路径，核对管理端/小程序消费者。

以真实职责决定改动：入口保留协议/身份，规则和事务归 Service，条件走具名 Mapper。核对 DTO/VO 公开字段、Jakarta 校验及现有分页/响应；合理薄类和主键 CRUD 可无 diff，不机械抽 Assembler 或新增层。

整理服务前核对代理事务、自调用及 Redis/文件/HTTP 副作用。命令在 yuweiju-backend 执行；按行为选择普通测试。api_smoke.ps1 含写入，先查环境和授权；skipTests 构建不能称测试通过。

交付职责链、消费者、验证和限制；规范偏差不扩大当前授权。
