# Sandcastle 任务运行时与 issue #19 复盘改进

本目录首次纳入主分支。运行时入口与隔离环境是复盘改进的必要依赖，不包含业务代码变更。以下历史记录保留其原始日期与验证范围；当前任务必须通过 `SANDCASTLE_TASK_CONFIG` 显式指定，禁止复用历史任务分支或自动恢复失败任务。

在 Linux / WSL 的项目目录执行：

```sh
npm ci
npm run check:types
# 无模型夹具；需要已有 sandcastle:yuweiju-dev 镜像与可用 Docker
SANDCASTLE_EVIDENCE=/absolute/new-evidence python3 .sandcastle/run-config-fixture.py
SANDCASTLE_EVIDENCE=/absolute/new-lifecycle-evidence npm run check:lifecycle
```

配置夹具使用当前 HEAD，验证 UTF-8、同次运行两轮缓存复用、分轮容器记录及候选环境挂载。测试不挂认证、不调用模型，也不访问原库。夹具会创建本地测试分支和保留恢复材料，不自动删除历史证据。

创建开发镜像先按 `.sandcastle/Dockerfile` 构建基础镜像 `sandcastle:yuweiju-afk-issue-4`，再按 [environment/README.md](environment/README.md) 构建 `sandcastle:yuweiju-dev`。本次复盘验证使用既有镜像，启动参数显式提供 UTF-8；新 Dockerfile 默认值需下一次重建生效。隔离全栈、训练及微信验证须按该文档另外启动，本 PR 未重新执行这些业务验收。

详细范围、实际验证和回退见 [交付报告](../docs/issue19-retro-runtime-report.md)。

## 历史准备记录

# 当前环境入口（2026-10-07）

本分支使用可配置任务和全栈隔离环境，详见 [environment/README.md](environment/README.md)。默认无模型验证不挂认证。任务入口必须指定 SANDCASTLE_TASK_CONFIG，不沿用 #4 分支/prompt。旧准备 clone 与 #4 成果保留未改。

下面为复制保留的 #4 准备历史记录；其中旧地址、镜像、试点命令只作历史来源，不能作为本分支的现行启动说明。

# Sandcastle preparation for issue #4

This checkout contains the verified setup and the accepted, locally integrated
issue #4 changes. The historical #4 run is complete; do not rerun main.ts or reuse
its existing task branch. A future AFK task needs explicit scope/branch/start-commit
authorization; this preparation update starts no coding agent.
Keep the setup branch, smoke branches, preserved worktrees, recovery directories,
and local logs until the maintainer reviews them. Do not push, merge, publish,
write GitHub, use the original database, or run main.ts without step 7 authorization.

Host: Ubuntu WSL, `/home/endercloud/projects/yuweiju-afk-issue-4`.
User-local Node 22.23.3 supplies node/npm/npx via `~/.local/bin` and the existing
`.profile`. Root package dependencies are Sandcastle 0.12.0 and tsx 4.23.15.
Image: `sandcastle:yuweiju-afk-issue-4`; pinned Node 22.23.3 and Codex CLI 0.160.1,
Debian/glibc, non-root UID/GID 1000:1000. No Java, database, Docker socket or gh.

The dedicated auth directory is `/home/endercloud/projects/yuweiju-afk-auth`,
mode 700, outside Git. Its config matches `codex-config.example.toml`; credentials
use file storage, ChatGPT only. It is mounted writable for refresh persistence.
Standard uses an unset/default service tier with Fast disabled, not a guessed
`--speed` argument. The account catalog exposes gpt-6.1-sol with medium; metadata
is not an inference or quota guarantee. Do not probe a different model or API key.

Static task documents and only the two specified personal skills live under
`/home/endercloud/projects/yuweiju-afk-input`, mounted read-only. CLI bundled
system skills are present as installed; no personal plugins/MCP are copied.

No-model verification entries:

```sh
cd /home/endercloud/projects/yuweiju-afk-issue-4
npx --no-install tsx .sandcastle/install-check.mts
npx --no-install tsx .sandcastle/smoke-run.mts
```

`install-check` calls createSandbox and exec only, never sandbox.run or a model
turn. `smoke-run` calls the installed package's real run entry, Docker provider,
branch/worktree lifecycle, stream parsing, completion detection and cleanup.
Scenarios: exit 7, normal exit without completion, completion signal, AbortSignal,
total deadline, host SIGINT/SIGTERM, idle timeout. All use new smoke branches,
local files, no authentication mount and no model. Smoke timing overrides leave
formal limits at 1 iteration, 600s idle, 60s completion grace, 30min total.

`main.ts` supervises `business-worker.mts` in a separate process group. Terminal
signals are sent as IPC cancellation and converted to AbortSignal. This avoids
Sandcastle 0.12.0's synchronous signal exit bypassing result recording, while
retaining its shutdown cleanup fallback and real provider close behavior.
`common.mts` records the exact worktree mount and owning container, then checks
removal in finally. The supervisor waits for the worker; it does not return from
a Promise.race while the container continues. SIGKILL/power/daemon failure still
requires recovery inspection of the recorded specific resources.

Evidence lives outside Git at `/home/endercloud/projects/yuweiju-afk-evidence`.
For a dirty run, recover committed content by Git ref and recover uncommitted
files from the recorded preserved worktree; smoke checks demonstrate both in a
new detached recovery worktree. Clean incomplete worktrees can be removed by
Sandcastle. Never infer uncommitted recovery from the mere presence of a branch.

The isolated ordinary prechecks use a separate worktree based on the original
commit. Full lint currently fails with the expected 22 .cjs errors. npm ci,
typecheck, two formatting tests, and build pass; build's >500 kB chunk warning
is retained. These are preparation observations, not AFK issue acceptance.

## Preparation efficiency update

Versioned task summary: .sandcastle/issue-4-summary.md; deploy the same file to
/home/endercloud/projects/yuweiju-afk-input/documents/issue-4-summary.md. The prompt
reads the summary and required standards first, then details only when needed.
The existing hook installs dependencies once per new container; the agent reuses
that successful Linux install. Fresh worktrees/containers and independent review
still prepare separately. Since issue #19, isolated Maven/npm caches are reused
only across iterations of the same run; independent review stays fresh.

The future agent writes .sandcastle/issue-4-progress.md inside its task worktree,
keeps it untracked and outside ignored logs, and updates it after each logical
step. Existing dirty-worktree retention keeps the progress and logs available;
the host result/resource records identify the actual location. Do not commit it
with the four task files. Recovery notes are supplied to a future run only after
new manual authorization and inspection of the retained commit/uncommitted files.
The current preparation update produces no business-agent progress or acceptance.

Worker result summaries now distinguish agent completion from independent review
and human acceptance, point to the candidate progress file and resource record,
and include the next step. Failure output contains one bounded error line and
the existing local diagnostics directory; complete error text remains in the
original boundedRun log/result records. Cancellation handlers and cleanup are
unchanged. See efficiency-report.md for the checks performed on this update.

## Completion and behavior-test requirements

A completion marker must follow successful checks AND an actual task commit.
Explicitly stage the authorized files, commit, inspect git show/status and put the
commit ID in progress. Missing commits now produce incomplete-missing-task-commit
and a nonzero worker exit, even if the agent emitted the marker. Retained files
remain available for manual review; no automatic retry or host commit is added.

The maintained admin package exposes npm run test (Vitest) and npm run test:refimg
(Node script behavior). Both are required for script changes. The permission
fixture requires Linux; native Windows exits nonzero with an explanation rather
than skipping. Full independent review runs as UID/GID 1000:1000. The explicit
node --test scripts/refimg-scripts.check.cjs command remains equivalent and is
used by the review entry to support older candidate commits without the alias.

## Independent verification

From the WSL clone root, review committed content without authentication/model:

```sh
python3 .sandcastle/verify-admin.py --commit <exact-local-task-commit>
```

This creates a new detached worktree and log directory under the existing evidence
root. The fixed image uses --entrypoint sh and non-root UID/GID. A new container
runs npm ci (5-minute limit), lint, typecheck, Vitest, build and the explicit Node
behavior check; the complete review has a 30-minute limit. Full logs, per-command
exits and specific container/process records are retained. No auth/skills/GitHub
mount is provided. The snapshot and logs survive container cleanup for review.

For an uncommitted candidate, first create an isolated Linux worktree under the
same evidence directory and copy only the reviewed changes, excluding node_modules.
Pass --snapshot /absolute/evidence/isolated-worktree instead of --commit. The
result explicitly labels it as a snapshot with uncommitted content; it does not
promote it to a committed task. This mode cannot target the original Windows
workspace or the active business worktree. Supply future recovery only after
manual authorization; the verification entry never starts an agent or resumes it.

---

# Sandcastle Git 防误操作改进

这里保存外部 WSL 通用环境的运行时覆盖文件和新增防护源码。完整可运行 clone 在本项目 `.scratch/sandcastle-git-safety/runtime`；外部旧环境、旧 #4 分支和成果保持不变。方案见 [实施方案](../docs/sandcastle-git-safety-plan.md)，依据见 [研究笔记](../docs/sandcastle-git-safety-review.md)。

## 准备与使用

在 Ubuntu WSL 中从项目根目录执行：

```sh
cd '/mnt/e/Learning Files/yuweiju-takeout'
export TMPDIR="$PWD/.scratch/sandcastle-git-safety/tmp"
export npm_config_cache="$PWD/.scratch/sandcastle-git-safety/npm-cache"
mkdir -p "$TMPDIR"
python3 .sandcastle/prepare-runtime.py
docker build -f .sandcastle/Dockerfile.git-safe -t sandcastle:yuweiju-dev-git-safe .sandcastle
cd .scratch/sandcastle-git-safety/runtime
npm run check:types
npm run check:git-policy
npm run check:git-guard
npm run check:lifecycle
```

准备脚本拒绝覆盖已有 runtime，不复制认证，也不配置 remote；依赖以 symlink 只读使用旧环境已安装的 Linux node_modules。只提交选定的运行时覆盖与 package 脚本，业务文件沿用来源提交，不吸收原工作区未提交修改。`preparation.json` 记录实际来源与准备提交。

clone 的本地提交身份默认为 `Sandcastle task agent <sandcastle@localhost>`；需要其他作者身份时在该 clone 内显式配置。`check:lifecycle` 在项目内建立小型真实 Git 测试仓库，运行相同的 Sandcastle 编排；它不复制业务素材，不替代业务验收。deadline 测试总时限为 20 秒，给 DrvFS 和防护启动留出空间；正式一轮/30 分钟及 idle/grace 设置未扩大。

未来任务按 AFK 流程选择本次 runtime 和准备提交，用任务专属配置将 image 指向 `sandcastle:yuweiju-dev-git-safe`；仅在任务另获编码授权后运行 `.sandcastle/main.ts`。本次维护及测试不启动真实编码代理。运行时默认 evidence 已改为 runtime 内的 `.scratch/sandcastle-evidence`，也可用项目内的 `SANDCASTLE_EVIDENCE` 覆盖。

如果旧配置仍选择原镜像，准备检查会失败；请更新镜像字段，不得跳过检查。修改 overlay 后应将对应文件同步至项目内 runtime 并单独形成新的准备提交，任务 startCommit 必须包含该提交；不要把未提交的准备修改当成可供 worktree 使用的版本。

## 保护范围

容器的 `/usr/bin/git` 与 `/usr/lib/git-core/git` 使用 root 持有的包装器。参数在执行前解析，支持 `git -C`、配置 alias、常见参数组合、绝对入口及子脚本调用。shell alias 与无法判断的选项/自定义命令拒绝执行。正常检查、显式 add、普通 commit、clean dry-run 和不会覆盖修改的恢复继续可用；纯 unstage 保留工作文件。

包装器拒绝 push、hard reset、实际删除的 clean、强制分支删除/重写、强制 checkout/switch，以及覆盖已修改或暂存文件的 checkout/restore。拒绝消息只输出类别和原因，不回显命令或凭据。通用 prompt 要求记录阻碍、保留成果、禁止绕过重试；依赖阻碍任务完成时不能发 COMPLETE。

安装检查位于依赖准备前及代理启动前；检查安装路径不可由代理修改、入口指向包装器、拒绝及普通操作实际可用。防护缺失时停止准备。宿主 Git 不经过包装器，既有分支/worktree 清理、取消、进度及结果保留仍按原实现执行。

经过任务准备审阅的 `installCommands` 在独立 hook 进程内将原生 Git 放在 PATH 首位，保留依赖准备所需的 Git 语义。这个 PATH 不传给代理；代理启动前重新验证默认入口。原生 Git 属于已说明的可绕过路径，因此这里只能执行已授权的依赖准备命令，不能把发布或清理其他任务放入安装命令。

项目目录位于 WSL DrvFS，完整 checkout 可能超过 Sandcastle 0.12.0 固定的 30 秒 worktree 创建时限。运行时先在原总时限及 AbortSignal 内异步创建本轮的新分支/worktree，再交给 Sandcastle 管理；已有分支或 worktree 仍须人工核对，不覆盖复用。准备失败后的部分目录保留待检查，不自动清除。

这是防误操作措施。委托二进制 `/opt/sandcastle/git-guard/real/git`、其他 Git 可执行文件和直接文件操作仍可绕过；策略未覆盖的 Git 操作也不能视为安全。例如 update-ref、stash/rebase 等仍沿用任务授权。现有共享 `.git` 挂载未重构。不得把本改进描述为任意命令都无法破坏数据的安全沙箱。

## 无模型集成测试

从项目内 runtime 执行，evidence 路径位于该 runtime 的 `.scratch/`：

```sh
SANDCASTLE_EVIDENCE="$PWD/.scratch/guard-complete" node --import ./node_modules/tsx/dist/loader.mjs .sandcastle/git-guard-run.mts complete
SANDCASTLE_EVIDENCE="$PWD/.scratch/guard-cancel" node --import ./node_modules/tsx/dist/loader.mjs .sandcastle/git-guard-run.mts cancel
SANDCASTLE_IMAGE=sandcastle:yuweiju-dev SANDCASTLE_EVIDENCE="$PWD/.scratch/guard-missing" node --import ./node_modules/tsx/dist/loader.mjs .sandcastle/git-guard-run.mts missing
```

前两例走实际 Sandcastle run、Docker provider、Git worktree 和 cleanup，核对提交/未提交内容与停机；最后一例确认原镜像缺防护时不会运行代理脚本。这些测试不挂认证、不调用模型、不 push、不访问原数据库。

正式授权后的启动命令使用已核实的绝对入口 `/home/endercloud/.nvm/versions/node/v22.23.3/bin/node --import ./node_modules/tsx/dist/loader.mjs .sandcastle/main.ts`（2026-10-09；启动前用 `--version` 复核）。WSL 非登录调用不加载 nvm；容器 Java/Maven 检查使用 `bash -c` 并确认 `command -v java` / `command -v mvn`，避免登录 shell 重置 PATH。直接加载 tsx loader 可避免 tsx CLI 在 DrvFS 下创建不支持的 Unix socket；TMPDIR 与 npm 缓存留在项目内。
