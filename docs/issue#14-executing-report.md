# Issue #14 执行与独立验证报告

2026-10-08。任务来源：https://github.com/Endercloud001/yuweiju-takeout/issues/14 。使用 yuweiju-afk；最多 2 轮、总时限 30 分钟。宿主开始 13:39:18，编码运行 13:41:41–13:48:38（Asia/Shanghai），实际 1 轮。为预留准备、验收、交付时间，编码配置 totalMs=960000、maxIterations=2；沿用 gpt-6.1-sol / medium、600 秒空闲超时、60 秒完成宽限及既有专用认证。整个交付在 30 分钟内完成。

## 交付与范围

- 原生依赖 #2 已 CLOSED；#14 为 OPEN、无评论。没有修改 GitHub 状态、发布或操作原库。
- 起点 bcbaa03be56dbd54d0deaa15374e6e9f354b0353，包含既有 Sandcastle 运行时；不是 Windows 当前分支的直接下一提交。
- AFK 任务提交 8d199b5137ffcafc84b7c7350984f438e87cb6fd；宿主探针修复提交 73265f253c68fdf326e77fc23921b4205c1754d6，为独立验收候选。
- 分支 codex/afk-issue-14-20261008 已导入 Windows；独立交付 worktree：`E:/Learning Files/yuweiju-issue14-delivery`。当前 Windows 工作区的 #19 修改、文件删除、未跟踪文件及分支保持原状；未合并两票。
- 两项任务提交的补丁：`E:/Learning Files/yuweiju-takeout/.scratch/issue14-afk-20261008/issue14.patch`。可在协调 #19 后评审/集成，不应把运行时准备历史误当成本票业务 diff。

管理员分页使用具名 OrdersMapper/XML，参数绑定并显式关联外层订单别名，保留状态、时间、总数、分页和排序语义。仅附加风险读取/装配失败降级；缺结果也返回既有字段 `riskLevel=UNAVAILABLE`、null 分数/模型及友好原因。风险筛选 SQL、核心查询及权限失败继续失败，无未筛选重试。管理端列表增加风险列，列表与独立详情明确显示“风险暂不可用”。

三端影响：后端仅管理员查询用例与持久化查询改变；管理端同步 nullable 风险类型与展示；小程序无需修改，因为它不消费管理员风险响应，用户详情与归属通过隔离 HTTP 回归。接口路径、字段类型、schema、金额、图片和认证语义不变，没有迁移。

最新时间相同的风险记录仍沿用原来的两个独立 EXISTS 条件；等级与分数可以分别匹配不同并列记录。展示仍以 evaluated_at DESC、model_version DESC 选一条，所以筛选匹配模型与显示模型可能不同。这是保留的已有语义，已通过真实 SQL 测试并在 API 补充文档说明；未重写风控算法。

## 改动文件与人工评审

共 13 个文件；相对上述起点比较候选：`git diff bcbaa03be56dbd54d0deaa15374e6e9f354b0353 73265f253c68fdf326e77fc23921b4205c1754d6`。

- `yuweiju-backend/src/main/java/com/codeying/mapper/OrdersMapper.java`
- `yuweiju-backend/src/main/resources/mapper/OrdersMapper.xml`
- `yuweiju-backend/src/main/java/com/codeying/service/impl/OrdersApplicationServiceImpl.java`
- `yuweiju-backend/src/test/java/com/codeying/service/impl/OrdersApplicationRiskIntegrationTest.java`
- `yuweiju-web-vue/yuweiju-admin/src/types/order.ts`
- `yuweiju-web-vue/yuweiju-admin/src/utils/order-risk.ts`
- `yuweiju-web-vue/yuweiju-admin/src/utils/order-risk.test.ts`
- `yuweiju-web-vue/yuweiju-admin/src/views/order/index.vue`
- `yuweiju-web-vue/yuweiju-admin/src/views/order/detail.vue`
- `.sandcastle/environment/Issue14Probe.java`
- `.sandcastle/environment/issue14-check.sh`
- `docs/issue14-plan.md`
- `yuweiju-document/api/issue14-admin-order.md`

评审时重点确认 catch 只覆盖附加风险、筛选失败不返回未筛选订单、外层关联及并列模型语义、列表和详情的不可用提示。与 #19 集成时保留双方 OrdersMapper 方法，并合并同路径 XML，不能覆盖 #19 未跟踪的 mapper XML。人工登录管理端检查 `/order` 与 `/order/detail/<隔离ID>` 的加载、空、错误和风险展示；不要对原库注入故障。回退两项任务提交并协调部署后端/管理端，无数据迁移；回退代码不逆转已有图片写回副作用。

## 独立验证证据

无模型、无认证挂载的独立验证镜像 `sandcastle:yuweiju-dev`，Java 21.0.9，非 root，LANG/LC_ALL=C.UTF-8。两次均从精确候选建立新 detached worktree，不复用编码缓存。

最终验收 snapshot：`/home/endercloud/projects/yuweiju-afk-issue14-20261008/evidence/task-review-1791438725842360744/snapshot`，HEAD 73265f253c68fdf326e77fc23921b4205c1754d6。

|工作目录|命令|退出码与业务结果|
|---|---|---|
|WSL 运行时 checkout|SANDCASTLE_TASK_CONFIG=<本次 config> SANDCASTLE_EVIDENCE=<本次 evidence> npx --no-install tsx .sandcastle/main.ts|0；1 轮，有任务提交及 COMPLETE 标记；不是独立业务验收|
|独立 snapshot 根|mvn -B -f yuweiju-backend/pom.xml package|0；23 tests，0 failures/errors/skips，BUILD SUCCESS|
|snapshot/yuweiju-web-vue/yuweiju-admin|npm ci; npm run lint; npm run typecheck; npm run test; npm run build|均 0；2 个 Vitest 文件、3 tests 通过|
|snapshot/yuweiju-backend|python3 /workspace/.sandcastle/environment/extract-classpath.py|0；生成运行探针 classpath|
|新容器 /workspace/yuweiju-backend|bash /environment/issue14-check.sh|0；真实 Spring/MyBatis/MySQL/HTTP，46 个具名断言通过，ISSUE14 ACCEPTANCE PASS|
|WSL 运行时 checkout|docker compose -p yuweiju-issue14-20261008 -f .sandcastle/environment/compose.yml down|0；本票服务/网络停止，数据卷与证据保留|
|候选 diff|git diff --check bcbaa03 codex/afk-issue-14-20261008|0|

真实探针使用新的 internal MySQL/Redis 网络与人工订单 914001–914005；验证状态/风险/分数/组合筛选、最新风险关联、时间边界、排序、分页总数、空页、无记录、绑定恶意输入、substring 修剪、并列模型展示。通过隔离表临时更名注入实际 SQL 故障，核对附加失败时分页与详情核心数据可读、风险明确不可用，筛选与核心订单/明细查询故障仍失败。未登录请求被拒绝，用户越权详情被拒绝，正常用户详情金额/图片回归通过。finally 恢复表名并清理本票合成订单，cleanup 断言通过。数据库不发布宿主端口。

首次候选 8d199b5 的独立构建通过，但真实探针启动 exit 1：仅启用 afk，缺少 AiWeatherService Bean。宿主将探针 profile 改为既有 dev,afk，提交 73265f2 后重新在新快照执行全部检查及运行验收，成功。没有重启、续跑或增加 AFK 轮数。

限制：没有执行浏览器视觉验收或小程序开发者工具验收；该票未改小程序。npm ci 报告既有 23 个依赖漏洞，构建存在大 chunk 提示，本票未调整依赖。历史 api-results.json、harness-results.json 缺失，不能作为本轮证据，也未对原库重跑脚本。编码代理把后端测试数量报告为 32，宿主按两次独立日志确认实际为 23，不采信代理数量。

## 资源、日志与恢复

本次输入/config/prompt：`/home/endercloud/projects/yuweiju-afk-issue14-20261008/`，仓库外保留。编码完整日志、进度、资源/result、独立 snapshot 和依赖缓存留在 WSL；没有复制或提交认证内容。

资源记录确认编码容器 31016d8082884bf08940a33b4b24539e93bace254b696ccc1d2159dfe1348af6 stopped=true；独立复核 resources.json stopped=true；运行探针容器 yuweiju-issue14-runtime-review 不存在；本票 Compose 服务已 down。本票没有全局 prune、删除数据卷或停止其他任务资源。

可直接读取的 Windows 证据目录：`E:/Learning Files/yuweiju-takeout/.scratch/issue14-afk-20261008/evidence/`，含：

- `runtime-final.log`、`runtime-final-result.json`、`runtime-assertions.txt`：最终运行、精确 snapshot commit、46 个业务断言。
- `independent-final-console.log`、`task-review-1791438725842360744/{check-0.log,check-1.log,check-2.log,exit-status.tsv,resources.json,environment.log}`：最终新快照完整检查及退出状态。
- `codex-afk-issue-14-20261008-1791438101676-{result,resources}.json`、`supervisor.log`：轮数、提交、完成信号、编码资源停止。
- `service-stop.log`：隔离服务停止记录。
- `runtime-initial-failure-excerpt.txt`：首次失败的已观察工具输出摘要。宿主运行驱动错误复用日志文件名，首次完整运行日志被第二次覆盖；仅保留已读取的错误摘录，不能声称保留完整首次日志。

恢复代码使用已导入分支/两项 task patch；编码未提交进度位于 `/home/endercloud/projects/yuweiju-sandcastle-env/.sandcastle/worktrees/codex-afk-issue-14-20261008/.sandcastle/task-progress.md`。重验可使用最终 snapshot 和新隔离服务，显式绑定候选；不要自动重新启动 AFK、覆盖旧 #4 任务、使用原库或共享其他票的数据卷。

## 宿主 /retro（本轮一次）

已先检查停止记录，再读取代理 log/result/progress、候选 diff、独立检查和运行失败/成功材料；采用 retro 及 writing-for-agents 指引。以下是环境改进建议，和本轮已经完成的业务/探针修复分开记录，不新增 gate/hash/baseline。

|证据|影响|建议|实施状态|
|---|---|---|---|
|首次探针 afk-only 缺 AiWeatherService；既有 README/EnvironmentProbe 已写 dev,afk|编译和 Mockito 无法发现真实 Spring profile 装配失败|任务 prompt 直接引用现有独立运行入口/profile，避免要求代理重写已确认启动事实|本票探针已修复；共享 prompt/技能尚未修改|
|宿主 runtime-review.py 重用 runtime-first 文件名，并硬编码初始 commit|首次完整失败日志丢失，第二次结果来源需要重新核对|宿主复核每次使用新的 attempt 目录，并读取 snapshot HEAD 记录候选；普通文件路径与 Git 足够，无需额外 hash/gate|最终记录已纠正、首次摘录保留；通用工具改造仅建议|
|编码报告 32 tests，真实 Maven 汇总为 23|代理完成文本可能夸大验证数量|交付始终读取 Surefire 汇总和独立 exit-status，优先日志而非代理口述|本轮已实施核对；无需新增测试或门禁|
|开始时 gh native dependencies 返回大量无关 repository/user 字段；WSL 无 rg|工具输出浪费上下文、跨环境定位失败|依赖读取用 --jq 投影 number/state；WSL 明确使用已有 grep 或安装/确认 rg 后再用|本轮后续采用精简投影/grep；没有变更环境依赖|

没有剩余实施阻塞。合并 #19 与人工视觉评审属于交付后协调；本次结果是独立候选已通过所列自动业务验收，不代表正式发布或原库操作已授权。
