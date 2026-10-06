---
name: yuweiju-stack
description: 修改余味居管理端 API 请求、响应类型、Vue Router 或 Pinia 状态链路时使用。
---

# 管理端技术链路

读 [管理端规范](../../../docs/standards/admin.md)，进入 yuweiju-web-vue/yuweiju-admin 查看实际 src/api/http.ts、模块、类型、router 与相关 store。

沿链确认 VITE_API_BASE、Cookie token、响应体/Blob 与错误提示责任；核对后端字段，不从旧模板生成另一套响应或守卫。登录/退出核对 Cookie、store、导航一致。

只调整当前任务模块与消费者，按 package.json 选择检查；记录实际路由、成功/失败/未登录结果及三端兼容影响。
