# 独立检查入口

优先使用已确认 main 的入口，并记录实际 checkout/HEAD 和候选；下方日期化路径是历史来源。编码前运行 `.sandcastle/preflight.py --config <配置>`，它不启动模型或写业务数据；真实双网络无模型回归入口为 `python3 .sandcastle/verify-preflight.py`。独立候选验证仍使用本页统一 verifier。

`verificationMs` 可独立设置检查时限，未填时兼容旧 `totalMs`。生成的 `check.sh` 在写入处固定 UTF-8/LF，包括 ignored 临时文件；resources 中 `checks` 分别报告 passed/failed/not-performed，后续未运行项不算通过，`humanAcceptance` 仍为 not-performed。

宿主入口复用根 package scripts：`npm run check:preflight`（真实镜像/双网络、空认证与取消）、`npm run check:config`（两轮真实工作树重建、缓存/证据承接及独立无模型验证）、`npm run check:evidence`（脱敏、选定文本、路径/大小边界）。前两项需宿主Docker；evidence 普通测试已接入 `checks.example.json` 的runtime组，可在独立容器内运行，无新增合并门槛。

从实际 checkout 读取 `package.json` 和本目录配置。先用 `git status --short`、`git rev-parse HEAD`、`git worktree list` 核对位置、候选和用户变更；项目根目录可能仍在旧分支。此次交付工作树为 `E:\Learning Files\yuweiju-takeout\.scratch\issue13-environment-improvements`，从远端 main 的 `e66ba4a` 创建；main 的后续变化仍需实时读取。历史 `.scratch/issue13-retro-integration` 保留，不能把它自动当作最新交付。

## Windows 和 Linux 共用 verifier

在上述 checkout 的 PowerShell 中，选择已提交的精确候选：

```powershell
$candidate = git rev-parse HEAD
./.sandcastle/verify-task.ps1 -Config ./.sandcastle/checks.example.json -Commit $candidate
```

`-Checkout` 可显式选择另一具名工作树，`-Distribution` 默认 Ubuntu；参数经 `wslpath` 转换后直接传给 Python，不经过嵌套 shell。`-Config` 使用 Windows 可读路径。脚本返回 verifier 的退出码，WSL 发行版或转换失败立即报错。Linux 直接调用 `python3 .sandcastle/verify-task.py --config .sandcastle/checks.example.json --commit <candidate>`。新任务也可继续使用原 task config 的 `checkCommands`。

默认证据位于选定 checkout 的 `.scratch/sandcastle-evidence/task-review-*`，也可用 `-Evidence` 指定项目内绝对路径。资源记录包含 repoRoot、gitDirectory、精确 commit、发行版、snapshot、容器名、命令、退出状态及 stopped；`check-N.log`、`exit-status.tsv`、`environment.log` 保留完整结果。快照只包含候选提交，未提交文件不会进入检查。失败命令阻止后续命令，未出现的检查不算通过。保留快照和历史日志，停机只处理本轮具名容器。

容器无认证挂载、无模型调用、无原库连接，使用非登录 bash 和 C.UTF-8。现有 Git-safe 镜像必须已准备；依赖下载需联网。网络连接和业务验收仍由任务授权决定。指定 shell 文件由 `.gitattributes` 保证 Windows 新 checkout 为 LF；旧工作树不自动重写，保留其中修改。

## 宿主证据摘要

在实际 checkout 执行 `python .sandcastle/read-verification.py <本票resources.json绝对路径>`。读取固定 UTF-8（兼容 BOM），输出来源、候选、退出状态、停机、独立/人工验收和最多40项检查；对应 check-N.log 仅提取每文件最后3条 Maven 数字摘要。完整日志与 commands 保留原文件，输出不包含原始正文、凭据或业务消息。超量项以 checksOmitted 明示；该工具只读，不运行检查，也不以自身 exit0 宣称业务通过。坏编码/JSON 返回非零及 errorType，不能使用 replace/ignore 解码制造成功。

PowerShell 手动读取中文日志用 `Get-Content -LiteralPath <路径> -Encoding utf8`；Python 使用显式 `encoding='utf-8-sig'`。查看失败上下文时按所需范围脱敏读取，不打印完整 resources/日志。容器 C.UTF-8 与宿主文件解码各自设置，不能互相代替。

## 可选 npm 下载缓存

重复独立检查可显式传入项目 `.scratch/` 内已有、仅存 npm 下载内容且容器 UID 1000 可写的目录：Windows 用 `-NpmCache <Windows路径>`，Linux 用 `--npm-cache <绝对Linux路径>`。verifier 将其挂载到 `/npm-cache`，设置 `npm_config_cache=/npm-cache`，在 resources 的 `npmCache` 记录来源；未指定时沿用原行为。保持目录为本任务/维护专用，缓存中不放 `.npmrc`、认证或业务文件，勿与正在写入的其他任务混用。

缓存仅复用下载内容。每个新候选快照继续执行 `npm ci --no-audit --no-fund` 及完整 lint/typecheck/test/build；原检查命令若带 `--cache` 会覆盖该环境变量，应移除该参数或改为 `/npm-cache`。缺包时照常下载，锁文件错误或安装失败照常失败。`--offline` 只用于已充分缓存的诊断，不能作为默认安装选项。保留新日志/退出码，node_modules 与通过证据不从旧候选复制。停机保留缓存；源码普通回退可移除选项，清缓存不会回退业务数据。

## 已有检查如何触发

| 检查 | 入口与结果 |
| --- | --- |
| runtime 类型与 policy | 上述 `checks.example.json` 第一组；复用根 package scripts |
| 后端 Maven 全部普通测试/package | 配置第二组；隔离快照的新 Maven 缓存，不用 skipTests |
| 管理端 lint/typecheck/test/test:refimg/build | 配置第三组；Linux 非 root 运行行为测试 |
| Git guard | Ubuntu 宿主实际 checkout 执行 `python3 .sandcastle/verify-git-guard.py`；`.scratch/git-guard-verification` 保留 verdict 和停机证据 |
| orchestration lifecycle | 宿主安装根依赖后执行 `npm run check:lifecycle`，见根 package scripts；`.scratch/lifecycle-verification` 保留结果 |
| 隔离全栈、训练、微信及业务探针 | 按 [environment](environment/README.md) 选择对应 verifier/脚本；需要任务专属服务及授权，普通检查配置不自动启动 |

后两项 runtime 检查要使用宿主 Docker，不能塞进未挂 Docker socket 的独立容器。宿主 Node 使用 [runtime](../.agents/skills/yuweiju-afk/references/runtime.md) 中已核实的绝对入口；运行前再次检查工具。每组命令、退出码和测试结论分别记录，不能以一个驱动 exit 0 代替业务验收。

2026-10-09 只读检查 GitHub Actions 工作流数、main check-runs 和 commit statuses 均为 0；仓库外服务是否存在未获证据，不能据此断言没有。这里提供手动可重复触发的统一入口，无新增 CI、提交钩子或强制合并 gate。后续如需自动反馈，可复用这份配置，另行处理镜像和宿主能力。
