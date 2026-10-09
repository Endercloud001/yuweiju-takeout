# 独立检查入口

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
