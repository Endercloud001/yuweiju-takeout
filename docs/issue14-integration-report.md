# Issue #14 整合、日志及浏览器验收

2026-10-08，继续完成关闭前的三项评审意见。本报告补充 `issue#14-executing-report.md`，其旧候选、路径、测试数量及“未做浏览器验收”仅表示首轮历史状态；本报告为当前整合结论。

## 整合及代码

复用已有整合提交 `4dd172bc41caacb87bf299157e4527e42051c25d`，基于远端 main `dfa565a823e47e09eece77b154c142afb36e371b`（已合入 #19 与 Sandcastle 运行时）。本轮在项目内建立独立 worktree `.scratch/issue14-ready-verification`，分支 `codex/issue-14-reviewed`；没有修改项目原工作区或既有外部 worktree。

OrdersMapper 和同名 XML 同时保留 #19 聚合/报表查询与 #14 管理员条件分页查询。PR 差异只涉及 #14 业务、消费者、探针、文档和截图，不带入旧准备分支的运行时历史。后端两处可选风险 catch 使用订单 ID 上下文及 throwable 记录完整 cause，友好响应没有增加内部异常信息。

浏览器截图发现风险列在右侧被固定操作列遮挡。已把风险列移至状态列旁，保留原有色值、字体及布局风格，加入文字与操作区不重叠的实际位置断言。翻转登录按钮有两层“登录”文本，浏览器探针改用可匹配该按钮的名称选择器；不修改业务登录。

三端：后端接口路径、schema、认证、金额和图片语义不变；管理端仅风险消费与列位置变化；小程序不消费管理员风险接口，无需改动。用户归属和详情隔离回归已通过。没有原库操作、远端写入、发布或新 gate/hash/baseline。

## 新鲜验证

独立验证无认证挂载、不调用模型，镜像 `sandcastle:yuweiju-dev`，非 root 运行构建和业务/浏览器检查。项目内从 Git archive 导出 4dd172b 的新快照；后续把本轮仅有的 Vue 列位置与探针选择器/几何断言改动同步到该验证快照。后端源码及 mapper 与 4dd172b 无差异，后端构建/SQL 结果继续适用；修改后的管理端检查及浏览器重新执行。Windows 导出 shell 换行转换为 LF，不改变源码逻辑。

|检查与工作目录|命令|结果|
|---|---|---|
|容器 /workspace/yuweiju-backend|mvn -B package|exit 0；35 tests，0 failures/errors/skips，含 #19 回归|
|同目录|python3 /environment/extract-classpath.py|exit 0|
|/workspace/yuweiju-web-vue/yuweiju-admin|npm ci；npm run lint/typecheck/test/build|均 exit 0；2 test files，3 tests；列位置改动后四项重新通过|
|内部 MySQL/Redis 网络的独立容器|bash /environment/issue14-check.sh|exit 0；46 个具名 SQL/HTTP 断言，ISSUE14 ACCEPTANCE PASS|
|同一隔离环境的新浏览器容器|bash /environment/issue14-browser-check.sh|exit 0；15 个浏览器断言通过，零 pageerror|
|项目内候选 worktree|git diff --check|exit 0|

SQL/HTTP 覆盖状态/风险/分数/时间、绑定、总数、分页/排序、空页、并列风险时间、可选风险失败、筛选失败不回退未筛选结果、核心订单/明细失败、认证/归属、用户金额图片回归，恢复表名并清理夹具。

Chromium 验证真实密码登录、未登录路由跳转、真实列表及详情 LOW/UNAVAILABLE、金额保留、真实空列表和不存在订单；延迟真实请求验证列表/详情加载与完成后清除。友好业务错误由浏览器 route.fulfill 注入，仅用于 UI 展示验收，不冒充真实 SQL 故障证据。15 个断言包括风险文字不被固定操作区覆盖，截图已人工检查。

截图在 `docs/verification-evidence/2026-10-08/issue14/`：`order-normal.png`、`detail-unavailable.png`、`order-empty.png`、`list-error.png`、`detail-error.png`。

## 证据、失败及资源停止

本轮所有新建项目文件在项目根内。原始日志及命令结果保留于 `.scratch/issue14-review-20261008-final2/`：

- `exit-status.tsv` 与 `maven-package.log`、`admin-*.log`：独立初始构建。
- `runtime-attempt4/sql-http-runner.log`：46 项通过及夹具恢复；该次 browser 因登录选择器未匹配失败。
- `runtime-attempt6/admin-*.log`、`browser-runner.log`、`browser-backend.log`、`results.json`、`resources.json`：最终四项管理端检查、15 项浏览器结果及停止记录。

保留准备失败：旧 Windows Python 不支持 tar extraction filter；临时脚本及 Windows 导出快照的 CRLF 不适合 bash；NTFS 无法满足 MySQL 私钥文件权限。通过现有路径内的安全归档提取、LF shell 快照、项目 `.scratch/issue14-mysql-linux/disk.img` 内 ext4 数据目录解决。没有因此修改原库或绕过业务安全措施；使用 ext4 是环境适配，未新增依赖/门禁。

MySQL 与 Redis 只连本票 internal 网络，未发布端口；数据及 ext4 镜像保留项目内。最终所有具名容器 stopped=true、网络已移除、浏览器合成订单清理成功。宿主会话已无该 ext4 挂载；loop 设备已请求 detach，当前 AUTOCLEAR=1，仍待 Docker/WSL 的剩余引用释放；没有 prune、删除旧证据或停止其他任务。镜像中内置依赖工具与容器运行本身不代表项目外创建交付文件。

既有依赖审计/大 chunk 警告没有在本票变更依赖解决。截图中表格原有固定操作区与右侧金额区域的视觉重叠未作无关重设计；新增风险列已实际清晰可见。未执行小程序原生编译，因为本票未修改其源码。

## 关闭及集成判断

本票四项业务验收及上述三项评审意见已落实，有新鲜独立证据。可以提交远端并创建正式 PR；与当前 main 为正常祖先关系，无旧准备分支的三处合并冲突。PR 评审及适用远端检查通过后，可合并并关闭 #14。本轮未执行 push、PR 创建、合并或关闭；合并不是正式部署授权。

回退本票提交即可回退代码，后端/管理端一起协调；无需 schema 迁移。旧代码会恢复可选风险错误传播，不应对原库重演故障；代码回退不撤销既有历史图片写回。
