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
