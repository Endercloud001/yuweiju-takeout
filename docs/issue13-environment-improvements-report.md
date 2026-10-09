# Issue #13 环境改进实施与验证

日期：2026-10-09（Asia/Shanghai）。本轮按维护者“按复盘改进描述实施”的授权落实环境改进；不启动 AFK 编码轮次，不写 GitHub，不推送/合并/发布，不访问原业务库。

## 交付位置与范围

最终工程入口：`E:\Learning Files\yuweiju-takeout\.scratch\issue13-environment-improvements`，分支 `codex/issue13-environment-improvements`。创建前 `git ls-remote origin refs/heads/main` 为 `e66ba4a0e802270db01b37392fe33aa5522f5e23`，本地源码候选提交为 `60d5eb62db8829bc84180894df6099b304e2f8d8`。后续交付提交只增加 AGENTS 输出约束和本报告，不改变被测运行时或检查配置。main 未因本次本地实施更新；用 `git log`/`git status` 获取实时状态。

根工作区仍在原分支；PLANS.md 删除、local-development.md 等用户变更及旧工作树均保留。根 AGENTS 仅删除第二份重复“项目文件输出”段，第一份完整保留；交付工作树的 AGENTS 同步一份输出要求，供后续集成。未把用户其他改动混入提交。

后端、管理端、小程序业务源码、API 和 schema 均未修改；三端只涉及既有检查的触发方式，无消费者迁移。回退可普通 revert 本轮两个交付提交；根工作区去重若要撤销，只恢复被删的重复段，不能还原整个 AGENTS。证据和历史工作树保留，不清卷、prune 或清理其他任务。

## 已实施的五项改进

1. **独立验证统一入口。** 新 `.sandcastle/verify-task.ps1` 复用现有 Python verifier 和 UTF-8 容器设置，`.sandcastle/checks.example.json` 复用现有 runtime、Maven、管理端命令。`.gitattributes` 仅指定环境目录 shell 与 verify-admin.sh 为 LF；没有修改全局 autocrlf 或重排工作树。生成的新快照保留命令、精确候选、逐项退出码和资源停止记录。
2. **GitHub 超时恢复。** 在现有 [issue tracker](agents/issue-tracker.md) 补 REST 的 PR 创建、评论、合并、Issue 关闭示例；超时后按远端对象/head/作者/正文读取确认再重试，读取失败保留未确认状态。DNS 路由单次重新解析，先 dry-run，保留 TLS 校验。没有新 hash、幂等标记、冻结 contract、baseline 或 gate。
3. **Windows/WSL 执行入口。** 显式 Ubuntu，直接参数传递和 wslpath 支持含空格路径；Python 将 Windows worktree 的 gitdir 指针映射为 Linux 路径，Git 子进程局部设置 GIT_DIR/GIT_WORK_TREE。resources.json 新记 repoRoot、gitDirectory、commit、distribution、container；默认发行版和 Windows `.git` 解析不再依赖操作者手工修正。
4. **已有检查的触发与反馈。** [独立检查入口](../.sandcastle/verification.md) 映射每组检查和结果位置，普通检查共用一份已实际执行的配置；guard/lifecycle/业务环境测试仍由 Ubuntu 宿主入口触发，避免在普通容器挂 Docker socket。2026-10-09 只读查询 Actions 工作流、main check-runs、commit statuses 均为 0；仓库外检查服务未获证据。本轮提供可重复手动反馈，不新增 CI/提交钩子/强制 gate。
5. **导航精简与最终工作树。** 根 AGENTS 去重，交付入口说明实际 checkout、历史 worktree 与 main 的关系；AFK runtime 和 README 指向同一份入口说明。使用者先核实 checkout，再读取工程 package，避免把原工作区或任务产物目录当作最新工程。

## 本轮新鲜验证

主检查从交付工作树 PowerShell 执行：

```powershell
./.sandcastle/verify-task.ps1 -Config ./.sandcastle/checks.example.json -Commit 60d5eb6 -Evidence 'E:/Learning Files/yuweiju-takeout/.scratch/issue13-environment-verification'
```

Ubuntu 建立精确候选 detached snapshot，容器 `/workspace` 执行配置命令，UID1000，非登录 bash，LANG/LC_ALL=C.UTF-8，不挂认证、不调用模型。

| 验证 | 结果 |
| --- | --- |
| Windows Git 新建 `.scratch/issue13-environment-lf-check` 工作树并逐字节检查 | exit 0，7 个指定 shell 文件均无 CR 字节 |
| PowerShell Parser 检查 verify-task.ps1 | 无语法错误 |
| Python py_compile verify-task.py | exit 0 |
| 相对链接检查：verification.md、README、runtime、issue-tracker | exit 0，failures=[] |
| npm ci、check:types、check:git-policy | 组 exit 0，policy 6 tests 通过 |
| Maven clean package，快照全新 `.scratch/m2` | 组 exit 0，38 tests，0 failures/errors/skips，BUILD SUCCESS |
| 管理端 npm ci、lint、typecheck、test、test:refimg、build | 组 exit 0，Vitest 3 tests、脚本行为 4 tests，build 成功 |
| 容器 Java/locale 环境记录 | native.encoding/sun.jnu.encoding=UTF-8，Java 21.0.9；中文报表测试通过 |
| 含空格 config/evidence、显式另选 Windows 新工作树、`exit 7` 后接第二命令 | 子进程 exit 7，第二命令未运行，resources exitCode=7/stopped=true |
| 主检查容器及两个受控失败容器 | resources stopped=true；docker ps 仅保留五个原验收 live 服务 |
| git diff --check | exit 0 |

主证据：项目根 `.scratch/issue13-environment-verification/task-review-1791547425546513617/` 的 `resources.json`、`environment.log`、`exit-status.tsv` 和 `check-{0,1,2}.log`。三项状态分别为 0/0/0，overall exitCode=0、stopped=true，distribution=Ubuntu。第一次失败夹具位于 `failure evidence/task-review-1791547471517142172`；显式 `pwsh -NoProfile -File` 子进程核对位于 `failure process/task-review-1791547823474414812`，观察 PowerShell child exit=7，核对驱动 exit 0。第一次工具宿主报 exit 1，不能用它判断子进程是否原样传回 7，因此执行了后一次进程级核对。

GitHub 具名只读恢复证据：`gh api repos/Endercloud001/yuweiju-takeout/commits/e66ba4a0e802270db01b37392fe33aa5522f5e23/check-runs` 首次 TLS handshake timeout，随后重读返回 total_count=0；status 查询 total_count=0，Actions workflows total_count=0。失败退出状态与其他批量命令不能混同，最终以每项响应为准。PR/评论/合并写入恢复流程按 GitHub 官方接口核对，但本轮未授权外部写入，因此没有实际制造写入超时或证明生产环境不会重复写入；下一次已授权操作使用该指南核对。

## 限制

guard/lifecycle 源码未变，本轮未重跑它们；入口和现有结果位置已明确，不将历史通过当作本轮通过。43 个业务数据库场景、训练、微信原生工具本轮未运行；环境维护不扩展到这些授权。管理端安装报告 23 个既有漏洞（1 low/5 moderate/15 high/2 critical），build 保留 >500 kB chunk 提示，没有擅自升级依赖。

本次实际消除的是新 Windows checkout 的 shell CRLF、默认 WSL 发行版误选和 Windows worktree 元数据解析失败；独立验收复用既有 UTF-8 verifier，中文模板验证通过。GitHub 网络故障仍可发生，恢复指南改善确认和重试，不能保证网络不再超时。
