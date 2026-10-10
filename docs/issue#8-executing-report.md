# Issue #8 实施报告

日期：2026-10-10（Asia/Shanghai）。[Issue #8](https://github.com/Endercloud001/yuweiju-takeout/issues/8) 的编码、精确提交独立验证已通过，隔离人工验收服务已就绪；人工验收与主线交付待维护者反馈。未推送、创建 PR、合并或关闭 issue。

## 授权、来源与环境

维护者授权最多两轮、编码累计 60 分钟，独立验证另限 20 分钟；独立验证通过后可自动启动本机隔离服务。收到全部人工验收通过后，允许必要提交、推送、PR、合并、同步和关闭。历史证据再次搜索无结果时，允许记录缺口并用本轮隔离业务验证补充。没有原库写入、真实微信配置变更、Release 或服务器部署授权。

本轮读取 issue 正文、评论、状态和原生 blocked_by：#8 OPEN、无评论，#2 CLOSED。GitHub main 与本地 main/origin/main 均为 `e6d62a7379b505713f182caafa45e14ed3c65fcb`，包含已合并 #7 和 AFK 预检/逐轮证据改进。原工作区仍在用户原分支，AGENTS.md、已删除 PLANS.md、local-development.md 及其他本机未提交资料保持。

读根入口、workflow、issue-tracker、domain/GLOSSARY/ADR 0001/0002、三端标准及后端技能。使用最新 main 的 runtime/verifier，不使用旧准备目录中的旧工具；旧目录仅作为已安装 Linux node_modules 的只读来源。运行时为项目内独立 clone，无 remote，无认证复制。WSL Ubuntu，Sandcastle 0.12.0；编码模型 `gpt-6.1-sol / medium`，idle 600 秒、completion grace 60 秒、预算内 closing 180 秒。专用认证挂载按现有配置保留；独立验证和人工服务不挂认证。

宿主根：`E:\Learning Files\yuweiju-takeout`。

- 任务输入、配置、日志：`.scratch/issue8-afk-20261010/`。
- runtime：该目录 `runtime/`；容器应用工作树为 runtime 下 `.sandcastle/worktrees/codex-afk-issue-8-20261010`，容器映射 `/home/agent/workspace`。
- 独立验证精确快照：该目录 `independent/task-review-1791609941495677730/snapshot`，容器映射 `/workspace`。
- 人工服务：`.scratch/issue8-live-20261010/`，使用上述独立快照的 jar 和管理端源码/已安装依赖。小程序副本在该目录 `miniapp/`。

上述为本机证据位置，不假设 GitHub fresh clone 包含忽略目录。

## 历史证据搜索与预检

在项目根执行 `rg --files --hidden --no-ignore -g miniapp-cart-add.json -g api-results.json -g browser-routes-results.json`，包含隐藏、忽略目录及全部保留快照，exit 1、零结果。三份历史 JSON 不可取得。issue 副本、材料缺失、原生依赖及抓取命令/退出状态见任务 `input/`，维护者明确允许新验证补充；未对原库重跑历史写入脚本。

镜像 `sandcastle:yuweiju-dev-git-safe`，ID `f9eea3daf27f23c61a7206d3402af47d4248d0540b522f8f227489d8c78edb54`，存在 Git 防护、Java 21.0.9/Maven 3.9.11、Python、Node、Chromium；专用 `codex login status` 成功。runtime `npm run check:types` exit 0。MySQL 8.4、Redis 7.4 镜像已有。

编码和独立验证各用新 Compose 项目、单独的 internal 数据网络与具名出口网络，数据库/Redis不发布端口。同一 image/networks 的 preflight exit 0、ready、容器已停止，确认工具/Git/HTTPS入口/MySQL及Redis端口。编码入口重复预检且只读检查认证，成功。HTTPS 4xx只证明可达，不作模型成功证据；编码实际产出另查 result。

准备阶段首次相对 Python 入口出现宿主路径解析错误；以确认过的 `/usr/bin/python3` 入口重跑预检成功。此时 supervisor 尚未启动，不消耗编码轮次；未改认证或依赖。

## 候选、范围与三端影响

分支：`codex/afk-issue-8-20261010`。业务提交 `b3e351fcce6d4801ac35fbf8e53127d71e6db464`，记录提交 `64d5622035dd698bee2fce135c5022443af31012`；后者为精确独立验证/人工服务候选。主线 `e6d62a7..64d5622` 共29个文件，`git diff --check` exit 0。提交仅显式暂存任务文件。工作树剩余 `?? .scratch/` 是保留的本轮产物，未遗漏业务源码。

变更：

- `UserCategoryController` 委派 Service，移出 status/type/sort Wrapper。
- `Category/Dish/SetmealApplicationServiceImpl` 使用具名目录条件，不再构造本组业务 Wrapper；保留规则、组装、事务及主键 CRUD。
- `Category/Dish/DishFlavor/Setmeal/SetmealDish` 的五个 Mapper、五个 Service接口和五个 ServiceImpl 增补具名筛选、分页、统计、关联删除方法；Mapper 内参数绑定简单 Wrapper。
- 三个目录业务异常增加 cause 构造器，相关 duplicate-name catch 保留原因。
- `CatalogRulesTest` 的4个普通测试；真实探针 `Issue8Probe.java`、`issue8-check.sh`、`issue8-browser.cjs`；实施方案、业务验证记录和任务进度。

详细链与合理未改类见候选 `docs/issue8-catalog-verification.md`；方案为 `yuweiju-document/execplans/Issue8 - 商品目录职责恢复.md`。Service公共用例继续通过 Spring 代理；未机械加层或重写 BaseMapper。

后端局部职责调整，API/schema/原菜单/ID/分页排序/认证保持。管理端 category/dish/setmeal 页面和小程序首页分类、口味及套餐选择继续消费既有协议，因此两前端无需源码修改，外观/素材不变。用户目录公开读取保持；禁用分类不联动其菜品售卖状态、无套餐关联的售卖菜品原有删除语义、套餐详情原有读取语义均未擅自改变。订单历史图片与其他领域未扩展。

## 编码结果与失败记录

supervisor exit 0，result 的 completion signal 为 COMPLETE，实际存在两项任务提交；只用了第1轮。编码从13:04:35.862至13:25:13.864，累计1238.002秒（20分38秒），含 worktree、下载、检查和收尾；小于60分钟。没有监督器重启、取消或额外轮次。结果、资源、代理日志及 `iteration-1/iteration.json` 分别核对；主资源和该轮 `stopped=true`。迭代结束证据、检查日志/截图已保留，未将 COMPLETE 单独当作验收通过。

编码检查后端 package 54 tests、0 failures/errors/skips；管理端 npm ci/lint/typecheck/test/build exit 0（Vitest 2文件/3tests）；真实 probe 158断言、清理成功，浏览器4项通过。曾在构建期间调整 Java import/格式，随后对最终源码再次 package；历史中间构建不代替最终结果。

首次真实 probe exit 1：误以为四个公开用户目录读取需要 JWT，实际 WebMvcConfiguration 明确排除它们。失败发生在目录写入前，finally清理本轮mock用户/key并确认既有五表未变。根据当前安全配置修正测试预期，在同一轮重跑后成功；未更改认证实现。失败 `probe-batch2-first.log` / `scenario-results-batch2-first.json` 和最终输出均保留，不隐藏失败。

## 精确提交独立验证

宿主使用最新 runtime `.sandcastle/verify-task.py`，指定 `64d5622035dd698bee2fce135c5022443af31012` 和 `independent-config.json`。独立网络为 `yuweiju-issue8-review-20261010_isolated` 及其出口网络；MySQL/Redis新卷，不复用编码库或下载缓存。新容器UID1000，UTF-8 locale，无认证挂载、无模型调用。外层1200秒总超时覆盖快照准备与检查，配置 `verificationMs=1200000`。约11分钟内完成（13:25至13:36），exit 0。

各命令从 `/workspace` 独立shell运行，admin命令先 cd 实际工程：

| 命令 | 独立结果 |
| --- | --- |
| `mvn -B -f yuweiju-backend/pom.xml package`（显式Java/Maven PATH） | exit 0；54tests，0 failures/errors/skips，BUILD SUCCESS |
| `cd yuweiju-web-vue/yuweiju-admin && npm ci` | exit 0；Linux新依赖 |
| 同目录 `npm run lint` | exit 0 |
| 同目录 `npm run typecheck` | exit 0 |
| 同目录 `npm run test` | exit 0；2文件/3tests |
| 同目录 `npm run build` | exit 0；原有chunk提示保留 |
| `bash .sandcastle/environment/issue8-check.sh --browser` | exit 0；158业务断言、businessPassed/cleanupPassed=true；浏览器4项通过 |

`independent/task-review-1791609941495677730/resources.json` 记录全部7步passed、精确commit、authMounted=false、modelCalled=false、stopped=true；`exit-status.tsv`逐条为0，`check-0..6.log`为独立原始输出。真实场景JSON和3张页面截图在独立 snapshot `.scratch/issue8/`。宿主查看了套餐截图，浏览器另外核对分类/菜品/套餐实际搜索响应及DOM。

业务结果覆盖分类/菜品/口味/套餐各组筛选、分页/排序/空结果、停售/禁用与关联/混合批次拒绝、疑似SQL输入参数绑定、管理员未登录及错误scope拒绝、公开目录现有行为、管理员编辑后用户可售/口味/套餐明细。真实MyBatis/MySQL，经Spring代理强制第二口味和第二套餐明细insert失败：根编辑、首笔新关联及原关联IDs/值/份数全部回滚，独立商品及口味保持。finally移除任务触发器/自有行/mock用户/key，并比较所有原有五张目录表记录不变。自增值消耗是隔离测试副作用，未声称回滚自增、Redis、文件或HTTP。普通Mockito测试不作事务证据。

独立检查没有失败，也未因未经验证的小修重跑。npm原有审计/构建提示未被本票解决或隐藏。官方小程序编译和维护者实际交互仍待人工检查。

## 人工服务与交接

按已授权自动启动项目 `yuweiju-issue8-live-20261010` 的MySQL/Redis/backend/gateway/admin五个容器。后端jar只读挂载上述独立构建产物；没有Codex认证挂载。服务仅发布 `127.0.0.1:18080` 后端、`127.0.0.1:18088` 管理端；数据库/Redis只接 internal网络。live使用独立新卷、synthetic夹具，以及单独的套餐分类/口味/套餐样本，没有访问原库。

Windows无代理readiness在13:38:38检查：HTML、Vite client/main资源、后端业务code、管理端三组未登录401/code0、前端代理四项用户目录/明细全部通过。记录 `readiness.json`，启动日志 `start.log`，容器归属 `containers.json`。合成账号 sandbox_admin / sandbox-only-login，仅用于该环境。小程序副本只将common/vendor.js的唯一baseUrl改为本机18080；原项目未改。live `mock-login=true`，便于隔离登录，不冒充真实微信身份交换。

完整步骤见 [人工验收](issue8-human-acceptance.md)。等待维护者全部必需项反馈，服务保持运行，未安排静默到期/清卷。停止入口 `.scratch/issue8-live-20261010/stop.ps1` 已用PowerShell解析器检查无语法错误；只停止本项目，保留数据卷。启动过的coding/review数据库服务及出口网络已按具名归属停止/移除，日志 code-stop.log/review-stop.log，卷与证据保留，其他任务不操作。

## 一次全流程复盘

先核对coding/independent stop记录，再读agent日志、逐轮归档、首次失败和最终JSON、候选diff及独立结果。以下均为建议，未自动修改技能或运行时，业务交付不等待环境维护：

1. **先核对端点真实权限再写探针。** 首次失败源于公开目录JWT假设；当前WebMvcConfiguration已有明确排除。建议把读取实际端点权限配置加入本票设计调查，减少测试自身误报；使用已有代码事实/普通行为断言，不增gate。状态：本票探针已纠正并独立通过；通用提示尚未改。
2. **历史材料检索覆盖完整项目。** 最初仅核对issue列出的原路径，维护者要求全目录复查后确认仍缺失。建议需要判断“材料不可取得”时一次执行含hidden/ignored的文件名搜索，并记录范围/exit；不自动重跑原库。状态：本票已执行及记录；技能尚未改。
3. **新main工具应优先于历史runtime提示。** 旧准备目录停在09c2962，而main已含预检/逐轮证据/20分钟验证字段。新clone正确复用了main工具，未覆盖历史runtime或用户分支。建议继续按现有startup文档核对来源；无需重复新增机制。状态：本票完成，现有最新版技能已有此规则。
4. **宿主Python入口与容器环境分开记录。** 首次宿主路径解析异常，绝对入口重跑成功；未确定其底层原因，不能称Python修复。建议准备时保留实际入口/工作目录及错误，让后续按同一路径排查，不为暂态异常新增依赖或重启supervisor。状态：本票证据限制已记录，未改运行时。

## 待办与恢复

人工全部验收、必要交付、issue关闭待维护者反馈。没有自动重启AFK。候选在项目内runtime任务分支可恢复，未提交原始产物在保留worktree及逐轮evidence。若反馈失败，先按具体验收步骤核对源码/日志和已用预算，遵守技能的失败后恢复授权；本次成功运行不构成任意新AFK许可。代码正常revert可回退本票，无schema迁移；不承诺撤销服务已提交目录/缓存副作用。

## 维护者验收反馈与分类显示修复（2026-10-10）

维护者选择64d5622，确认1/3/4/5通过，2业务功能正常，但新增菜品/套餐分类的名称、排序输入后仍显示浅灰提示，提供两张截图；要求仅修此显示并停止服务。本反馈确认业务验收通过，另有显示缺陷，未被转述为无缺陷全部通过。

已运行live `stop.ps1`，exit 0；本票五个容器及两个网络停止/移除，按Compose项目查询容器为空，数据卷保留。日志 `.scratch/issue8-live-20261010/manual-stop.log`。后续显示验证只启动`--network none`的临时前端浏览器/检查容器，模拟页面API，不启动后端、数据库或原验收服务，也无原库写入。

修复文件为候选任务工作树 `yuweiju-web-vue/yuweiju-admin/src/views/category/index.vue`，只改一行：`.vanish-input[data-active='true'] / [data-has-value='true']` 对自定义提示的opacity由0.72改0。v-model、接口、业务规则、布局/素材及其他端源码均未改。两个新增弹窗共用这条样式。

浏览器在真实Vue路由复现：输入“菜品分类测试1”后计算opacity=0.72，隐藏断言失败exit1，截图与维护者症状一致。最初验证脚本自身两次未到达症状：按钮accessible name含“+”，宽泛mock路径还误拦`/src/api/`模块；已分别校正按钮名和只mock根`/api/`，未改产品以迎合脚本。样式改为0后，名称/排序输入中、失焦后、清空、排序0以及两字段同时填写的实际CSS状态与截图检查通过；空白失焦仍显示提示。工具与JSON/修复前后截图在 `.scratch/issue8-ui-fix-20261010/`，不是新的业务验收声明或新增正式测试套件。

Linux非root、无网络容器复用已安装依赖，执行管理端lint/typecheck/test/build，四项exit0；Vitest仍为2文件/3tests，build成功。无后端差异，原64d5622的后端/真实SQL独立结果继续适用；显示修复浏览器结果单独记录，不冒称原提交含该修复。

此追加显示修复保留为任务工作树中未提交的一行CSS；没有重启AFK、重新启动人工服务、额外提交、推送、PR、合并或关闭。维护者对修复后的显示尚未给出新人工反馈。此前“服务保留运行”仅描述首次交接，现在已按要求停止。
