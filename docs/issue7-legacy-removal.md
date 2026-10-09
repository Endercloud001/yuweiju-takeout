# 旧 Thymeleaf 管理入口删除方案与结果

2026-10-10，维护者确认真实环境验收通过，并明确授权：不保留 Thymeleaf 管理页面任何代码，以 Vue 管理端为唯一管理入口。此决定替代 Issue #7 原先“不自动删除旧模板”的排除项。真实验收为维护者声明，本轮不重新操作原库。

范围：删除 templates、专用 static/assets、AdminPortalController/AdminManagementController、旧 BaseController、Admin/LoginUser、AdminService/Impl、AdminMapper、PagerFooterVO、CaptchaServlet 和旧功能测试。保留员工/用户 JWT、黑名单、权限及现有 REST 接口。

三端与数据：后端旧页面入口及公开注册停止提供；Vue 管理端调用 /admin/employee/*，小程序调用 /user/*，均不依赖旧 session 或资源，无需修改。API 文档与数据库设计没有把旧入口作为两端接口；不删除 tb_admin 表、不迁移或清理原数据。

步骤：核对引用与消费者；删除完整旧调用链；调整隔离探针验证旧入口/资源不可用及管理端 JWT 保留；执行后端 clean package、管理端检查和真实隔离 SQL/HTTP/Redis/页面登录；更新 PR #32。

回退：Git revert 恢复代码，无数据库迁移。已使用旧 URL 的外部消费者将失去入口，需改用 Vue 管理端；不增加兼容注册入口或旧页面重定向。

验证：

- 隔离容器运行 `mvn -B -f yuweiju-backend/pom.xml clean package`：50 tests，0 failures/errors/skips，exit 0。较先前少 4 项，原因是删除旧功能的 3 项 Service 测试和 1 项页面协议测试；JWT/session 无凭据拒绝测试继续保留。
- 管理端 `npm ci`、lint、typecheck、test、build：exit 0，3 tests / 2 files 全部通过。
- `bash .sandcastle/environment/issue7-check.sh --browser`：exit 0，真实隔离 MySQL/Spring/HTTP/Redis、旧入口删除及管理端 JWT 边界、Vue 合成密码页面登录均通过。
- 实际 JAR 检查：旧模板、专用 assets、旧页面 Controller、Admin/LoginUser/Mapper/Service、BaseController、PagerFooterVO、CaptchaServlet 均无条目；源代码引用扫描也无旧调用链残留。
- 初次探针预期旧 URL 返回 404，但现有 GlobalExceptionHandler 将缺失资源转换为 HTTP 200/code=0；修正探针以同时核对处理器缺失与请求失败，未修改现有异常响应策略。无凭据 /admin 旧路径仍 401；有效 JWT 请求旧路径返回失败，不再提供页面。
- 探针成功后的辅助驱动曾因 Windows 换行符在 exit 参数中产生非零退出，转为 UTF-8/LF 后完整重跑 exit 0；保留初次失败及修复记录。
- 本轮 Docker 容器、Compose 网络和公网网络均已停止/移除，隔离数据库卷保留。无模型调用、无原库操作，也未重启用户既有服务。

本地证据：`.scratch/issue7-legacy-removal-20261010/` 的 backend.log、admin.log、probe-retry.log、status.tsv、exit-code.txt 和资源停止日志。维护者先前确认真实环境验收通过；本轮页面验证使用隔离账号，不冒充再次真实微信验收。
