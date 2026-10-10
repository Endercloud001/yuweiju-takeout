# Issue #8 复盘维护实施报告

2026-10-10（Asia/Shanghai）。本轮按交接的 1→2→3→4 顺序实施：第 1、2、4 项补齐准备/记录提示，第 3 项核对已有实现后跳过。修改仅在隔离维护工作树。维护实现后，用户追加授权本维护任务的 PR、提交、推送和同步；合并/部署未授权，交付回读另保留在本机 `.scratch/evidence/`。没有改变监督器、preflight 或 verifier 行为，也没有新增 hash、冻结 contract、baseline 或 gate。

## 授权、起点与范围

来源：项目根 `.scratch/issue8-retro-handoff.md`、[执行报告的一次全流程复盘](issue%238-executing-report.md#一次全流程复盘)、[交付记录](issue8-delivery-report.md)。当前聊天承接交接要求的新聊天实施，不再创建聊天。

开始时 `git ls-remote origin refs/heads/main` exit 0，确认远端 main 仍为 b8e099fedc2877c1731eee80344e2e52cb32b83e。维护工作树为 `E:/Learning Files/yuweiju-takeout/.scratch/issue8-retro-20261010`，分支 `codex/issue8-retro-20261010`，从该提交创建；读取该工作树的根 AGENTS/workflow 与 main 版技能。根工作区仍为 ecd2029 / `codex/issue-4-refimg-scripts`；AGENTS.md 修改、PLANS.md 删除、local-development.md 修改及未跟踪材料保持。

共享 `.git/info/exclude` 仅追加精确 `/.scratch/issue8-retro-20261010/`，`git check-ignore -v` 确认生效，防止维护工作树混入根工作区待提交材料；用户 .gitignore 未修改。可评审方案在维护工作树 `.scratch/maintenance-plan.md`，本轮验证原始记录在 `.scratch/evidence/`。

后端、管理端和小程序均无源码改动，API/schema/数据也无调整：本轮只改善宿主探针准备、材料检索与入口证据记录，因此三端不需修改。维护实施阶段没有 GitHub 写入或提交/推送/PR；后续交付按追加授权执行。全程没有真实 AFK、业务服务恢复、原库访问、合并、部署或重开 issue #8。

## 逐项结果

| 顺序 | 状态 | 已核实事实与本轮改动 |
| --- | --- | --- |
| 1 | 本轮实施；本票探针已解决 | 首次日志以 `AssertionError: missing authentication denied /user/category/list` 失败；真实 WebMvcConfiguration 排除四组用户目录路由。当前探针已改为匿名成功且无关身份头不影响公开读取，独立结果通过。通用准备提示缺少这项核对，现补入 [startup](../.agents/skills/yuweiju-afk/references/startup.md)：沿路由检查权限配置/拦截器/入口身份归属，为匿名、正确/错误身份分别确定预期和源码来源，保留受保护端点拒绝断言。测试假设不授权修改认证。 |
| 2 | 本轮实施；本票历史检索已解决 | 执行报告已有全根 hidden/ignored 搜索 exit 1 的记录，但 startup 无此通用步骤。现补入同一参考文档：材料判缺失前按文件名覆盖完整项目、保留快照，记录根目录/范围/命中/exit/stderr；错误不能作缺失，命中先查归属/版本，缺失不自动重跑原库写入脚本。 |
| 3 | 已解决跳过 | main 的 startup 首段明确确认 main 与 runtime/verifier 来源；[runtime](../.agents/skills/yuweiju-afk/references/runtime.md) discovery 和 Prepare 明确旧 checkout 仅为历史提示；[verification](../.sandcastle/verification.md) 首段同样要求优先已确认 main。本轮从该 main 建树，`.sandcastle` 与技能主入口 Git diff exit 0，未重复改造。 |
| 4 | 本轮实施提示；历史根因未证实 | 旧报告记载首次相对 Python 入口解析失败、绝对 `/usr/bin/python3` 成功；保留两次成功 preflight 的记录，未找到首次失败的独立入口日志。本轮在 runtime 命令段明确 Windows/WSL 宿主与容器分别记录 executable/entrypoint、argv、cwd、script/bind、exit、脱敏错误；相对失败/绝对成功须保留两次证据，不据此声称 PATH/依赖/cwd 已修复。没有运行时修复声明。 |

两份参考文档共增加三个段落。现有 AFK SKILL.md 第 1/2 步分别指向 startup/runtime，后端技能无需重复写同一规则。未选项：无；受阻实施项：无。

## 适用场景与验证

以下宿主命令 cwd 均为上述维护工作树；搜索显式选择项目总根 `E:/Learning Files/yuweiju-takeout`，避免只搜索维护 checkout。

| 检查 | 结果与证据 |
| --- | --- |
| 端点权限应用场景 | 按源码确认 `/user/category/list`、`/user/dish/list`、`/user/setmeal/list`、`/user/setmeal/dish/**` 为公开目录；管理员目录及受保护用户入口仍有未登录/错误身份拒绝预期。首次错误断言和最终独立断言筛选保存在 `endpoint-scenario.json`；属于本轮源码/历史证据核对，没有重跑业务请求。 |
| 历史文件完整搜索 | `rg --files --hidden --no-ignore <project-root> -g miniapp-cart-add.json -g api-results.json -g browser-routes-results.json`：exit 1、零命中、无 stderr。范围覆盖 hidden/ignored 及保留快照；`history-search.json` 与 stderr 文件保留。只说明该范围内本次不可取得。 |
| hidden/ignored 检索场景 | 在本轮证据目录 `.retained/` 和 `node_modules/` 各放同名合成 JSON。普通 `rg --files <fixture>` exit 1、零命中；全项目 `--hidden --no-ignore` exit 0、精确命中两份，无 stderr。`search-scenario.json`。使用独立合成文件名，不干扰三份真实历史材料的缺失结论。 |
| 最新来源与源码不变 | `git rev-parse HEAD` 为确认 main；`git diff --exit-code b8e099f -- .sandcastle .agents/skills/yuweiju-afk/SKILL.md yuweiju-backend yuweiju-web-vue yuweiju-weixin-miniapp` exit 0。`957d247..b8e099f` 的后端与本票三份业务探针脚本 Git diff 同为 0。原统一独立结果继续适用，不重复 lifecycle/业务检查。 |
| Windows Python | `E:/Miniconda3/python.exe .scratch/record-environment.py host` exit 0；实际 executable 为该 Python，cwd 为维护工作树。`python-windows.json`、`python-host-exits.json`。WindowsApps 的 python3 别名不作可用性证明。 |
| WSL Python | `wsl.exe -d Ubuntu --exec /usr/bin/python3 <maintenance-root>/.scratch/record-environment.py wsl-host` exit 0；executable 为 `/usr/bin/python3`，cwd 为对应 `/mnt/e/.../issue8-retro-20261010`。`python-wsl.json`。 |
| 容器 Python | Git-safe 现有镜像，`--network none --user 1000:1000 --read-only --workdir /workspace --entrypoint /usr/bin/python3`，维护树只读 bind；记录脚本 exit 0，实际 cwd `/workspace`、script `/workspace/.scratch/record-environment.py`。无认证挂载/模型/数据库请求，`--rm` 后按具名容器查询为空，exit 0。`python-container.json`、`python-container-resources.json`。 |
| 文档链接与差异 | 现有 `check-doc-links.py` 检查两份参考文档及本报告，无失败、exit 0；`git diff --check` exit 0。本轮为参考文档应用场景核对和无模型工具验证，没有新增永久测试套件，也不能证明未来模型必然遵循提示。 |

历史失败来源：项目根 `.scratch/issue8-afk-20261010/evidence/codex-afk-issue-8-20261010-1791608675856-iterations/iteration-1/files/.scratch/issue8/probe-batch2-first.log`；最终独立业务 JSON 位于同任务 `independent/task-review-1791609941495677730/snapshot/.scratch/issue8/scenario-results.json`。统一独立结果的 resources 记录全部 7 步通过、无认证/无模型、stopped=true。成功 preflight 为 `evidence/preflight-1791608656212155787/preflight.json` 和 `preflight-1791608673308300526/preflight.json`。原文件及历史报告均未修改。

## 资源、限制与恢复

先读原 coding/review stop 和 live manual-stop 日志，再查询实际 Docker 状态。按 issue8 名称查询容器、网络均为空，查询 exit 均为 0；coding/live/review 的三个 MySQL 卷保留。`issue8-resources.json` 记录只读查询。唯一新增临时 Python 容器已退出/移除，没有新增网络或卷，未操作其他任务。

明确未完成：首次 Python 相对入口失败的完整 argv/cwd/error 原始记录与根因仍缺证，未为它造依赖修复或故意制造不同错误来冒充复现；三份历史 JSON 仍缺失。两者不阻止本轮提示补齐，不自动启动排查/原库脚本。后续有新增证据时由维护者决定是否调查。

恢复审查从本维护分支与 main 的 Git diff、本报告和 `.scratch/evidence/` 开始。撤销可正常 revert 本维护提交，保留本地历史证据；不用数据迁移或业务服务。仅提交本任务两份参考文档和本报告，推送具名维护分支并创建对应 PR，安全同步引用且保持根工作区分支和修改。PR 合并等待授权，不对已关闭的业务 issue 重复评论或操作。
