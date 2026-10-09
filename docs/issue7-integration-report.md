> 2026-10-10 后续授权：维护者确认真实环境验收通过，并决定完整删除旧 Thymeleaf 管理入口，Vue 管理端作为唯一管理入口。本报告以下内容记录删除前的集成和验证过程；当前范围与验证见 [删除记录](issue7-legacy-removal.md)。真实验收为维护者确认，本轮未获取额外凭据或重跑原库。

# Issue #7 主线集成与评审报告

日期：2026-10-10（Asia/Shanghai）。维护者已确认隔离环境的三步人工验收符合预期，授权评审/集成候选并创建 PR。任务关联 [Issue #7](https://github.com/Endercloud001/yuweiju-takeout/issues/7)。本 PR 不自动关闭 Issue，未授权直接合并或发布。

## 范围与集成

- 主线基点：`b76fdfd8c614ce01ca6b49418d643754175d6777`；推送前再次 fetch 核对未改变。
- 项目内独立 worktree：`.scratch/issue7-pr-20261010`；分支 `codex/issue7-integration-20261010`。用户当前分支及未提交修改保持。
- 原候选 e78e05a 的三项业务提交 cherry-pick 为 b7a4e8d、e2a38b5、0933449，不带入旧 Sandcastle 准备历史。
- 原候选基于 #19 之前的代码。UserMapper、ReportApplicationServiceImpl、WorkspaceApplicationServiceImpl 的冲突依据已合并 #19/PR #24 解决：完整保留主线报表/工作台实现、UserMapper 的 countCreatedInRange/countCreatedThrough SQL；只增加 findByOpenid。UserService 的统计辅助方法复用既有 Mapper SQL，没有重复查询定义。
- 集成探针随后发现旧方法名调用，08bce97 将其改为主线 countCreatedInRange；未改变业务源码。最终业务源码与已完整构建的 0933449 一致。

用户登录规则归 UserService，员工与旧模板业务条件归具名 Mapper，保留主键 CRUD、公开路径/字段/类型/code/schema/原 ID、既有密码/token/dev 微信回退。微信生产实现的上游失败保留 cause，并通过专用安全 handler 避免记录第三方 URL/正文或凭据；失败 code 仍为 0。旧模板路由和认证覆盖的事实与缺口见 [认证调查](issue7-auth-audit.md)，实施范围见 [计划](issue7-plan.md)。

三端：后端职责改变；管理端与小程序接口消费者不变，源代码无需修改。管理端做完整检查与实际页面合成密码登录；用户隔离经真实 HTTP/SQL 检查，不代替真实微信验证。

## Standards

独立只读评审 `b76fdfd...0933449`：未发现可操作的规范违规或代码异味。代码保留 Controller/Service/Mapper、具名查询、主键 CRUD、构造器注入；异常保留 cause 并隐藏上游敏感信息。隔离探针先检查资源边界，仅清理自有 fixture。未新增 schema、依赖、客户端改动或 hash/冻结 contract/baseline/gate。后续只修正探针方法名并补充文档/截图。

## Spec

独立只读评审：未发现新增必须修复项。登录/员工规则整理、具名 Mapper 与旧模板路由/认证调查已实现。原有账号页面登录、真实微信验收仍缺证据；隔离账号的三步人工验收不能替代。既有员工角色/禁用 JWT、旧模板公开注册和 session/JWT 不一致均按本票排除项保留，不宣称生产认证完成。

两轴汇总：Standards 可操作问题 0；Spec 新增实现问题 0、未完整验收证据类别 2。验证执行另发现并修正 1 处隔离探针方法名兼容问题。

## 集成验证

从 093344965cd62483d82fad6c883ac15f0fb362b3 导出新 detached snapshot，使用无模型/无 Codex 认证挂载的新容器，LANG/LC_ALL=C.UTF-8。新建隔离 MySQL/Redis 数据卷与内部网络；验证容器另接专属公网网络仅供依赖下载，数据服务不发布宿主端口。没有原库操作。

| 工作目录 | 命令 | 退出状态和业务结果 |
| --- | --- | --- |
| snapshot 根 | `mvn -B -f yuweiju-backend/pom.xml clean package`（PATH 含 Java 21/Maven 实际目录） | 0；54 tests，0 failures/errors/skips，BUILD SUCCESS |
| snapshot/yuweiju-web-vue/yuweiju-admin | `npm ci` | 0 |
| 同上 | `npm run lint` | 0 |
| 同上 | `npm run typecheck` | 0 |
| 同上 | `npm run test` | 0；2 tests passed |
| 同上 | `npm run build` | 0；既有大 chunk 提示保留 |
| snapshot 根（08bce97 的 environment 只读覆盖挂载） | `bash .sandcastle/environment/issue7-check.sh --browser` | 0；REAL_SQL_SPRING_HTTP_REDIS_PASS、LEGACY_HANDLER_AND_JWT_COVERAGE_PASS、合成密码实际 PAGE 登录通过 |
| 集成 worktree | `git diff --exit-code 0933449 HEAD -- yuweiju-backend yuweiju-web-vue yuweiju-weixin-miniapp` | 0；探针修正后业务源码未改变，因此复用本次已测试源码的 jar/依赖，只重跑受影响的探针/浏览器检查 |

真实检查覆盖凭据/禁用/错误密码，员工筛选、排序、空页、状态、用户名参数绑定和密码归属规则；微信 mock 身份复用与不同用户地址隔离；统计 Mapper 与直接绑定 SQL 对照；管理员/用户签名隔离、Redis 黑名单、确切 Spring 旧路由注册和未登录访问 HTTP 401。探针清理自有合成员工/用户/地址/缓存并关闭 Spring、Vite/浏览器。

[合成密码页面登录截图](verification-evidence/2026-10-10/issue7-synthetic-page-login.png) 已由宿主检查，显示进入工作台。只使用合成账号；未入库维护者原图中的手机号、密码输入或令牌。

首次相对 Python 入口在启动时报告路径解析失败，改用绝对入口后运行；原错误日志保留。第一次完整检查最后探针编译失败：旧 countCreatedBetween 方法名不存在。业务构建/前端检查当时均已通过；探针改为 countCreatedInRange 后 javac、真实 SQL/HTTP/Redis 与浏览器检查通过。未将历史驱动的总体 exit 1 改写为成功。

证据目录：`.scratch/issue7-pr-verification-20261010/`，含 `verifier-initial-import-failure.log`、`verification-runner.log`、独立目录 `check-0.log`–`check-6.log`/`exit-status.tsv`/`resources.json`、`probe-final.log`、`business-source-unchanged.log`。首轮 driver 总体 exit 1/stopped=true；修正后的单独 probe exit 0，其容器已移除，无认证/模型。文档与截图提交不会修改已测试业务源码和探针。

## 人工验收与待办

维护者于 2026-10-10 确认隔离 e78e05a 的三个步骤：错误密码拒绝/正常页面登录、员工管理操作、退出并刷新受保护页面，行为符合预期。#7 人工服务已按要求停止，数据卷保留。本次新集成源码不改变这些前端交互。

待补证据：原有账号正常密码页面演示、真实微信上游登录与用户隔离。隔离 schema 无 tb_admin，CaptchaServlet mappings=[]；旧模板 SQL/渲染/验证码可用性不能声称已验证。旧模板保留/停用/认证改变，以及员工角色/禁用后 JWT 政策均需另行决定。本 PR 使用 Refs #7，保留 Issue 打开状态。

## 资源与回退

本次两个检查容器均已停止/移除，新 Compose 项目已 down，专属公网网络已移除，项目标签查询剩余容器为空。隔离数据卷、日志和 snapshot 保留；既有 #13 live 服务仍运行，未做全局清理或删除原数据。

回退属于 two-way door：评审后可按实际合并方式回退本 PR 的代码，无 schema 迁移。但代码回退不撤销未来登录创建用户、Redis 或外部请求副作用。Blast radius 为登录/员工/旧模板职责；报表/工作台源码保持主线。
