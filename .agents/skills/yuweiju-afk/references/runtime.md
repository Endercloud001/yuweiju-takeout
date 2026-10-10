# Runtime entry

Use these paths as discovery hints. Check the files before use:

- WSL distribution: `Ubuntu`.
- Prefer the runtime/verifier in the confirmed main checkout. The old prepared Git-protected checkout `/mnt/e/Learning Files/yuweiju-takeout/.scratch/sandcastle-git-safety/runtime` is a historical discovery hint, not automatically current. Read the selected checkout's `.sandcastle/README.md`, compare its HEAD/runtime with confirmed main, and preserve user changes; quote paths containing spaces. For a prepared clone, inspect `preparation.json` and actual HEAD/config.
- Previous general checkout: `/home/endercloud/projects/yuweiju-sandcastle-env`, retained as a read-only source and fallback; it does not include the Git guard.
- Old pilot checkout: `/home/endercloud/projects/yuweiju-afk-issue-4`. Keep its completed #4 branch and results.
- Environment instructions: `.sandcastle/environment/README.md` in the general checkout. Read only the sections needed for the task.

The general runtime uses `.sandcastle/task-config.mts`, `common.mts`, `main.ts`, and `launch.mts`. It currently uses Sandcastle 0.12.0. Read `business-worker.mts` for model settings. Read the config and `common.mts` for defaults: 1 iteration, 30 minutes total, 600 seconds idle, and 60 seconds completion grace. Confirm these values in the selected checkout.

The protected runtime requires the Git-safe image named by its config example. Read `.sandcastle/README.md` when preparing or diagnosing guard failures. Readiness runs before dependencies and again before the agent; a missing guard is a preparation failure. On `SANDCASTLE_GIT_BLOCKED`, preserve progress, report the operation category and blocker, and stop if that operation is needed to finish. Host publication follows task authorization. The wrapper reduces accidents; alternate binaries and filesystem writes remain outside its boundary.

## Prepare

First complete [startup handoff and evidence selection](startup.md). The selected runtime's `preflight.py` checks the actual image/networks before coding; business-worker repeats it with a read-only dedicated-login check. Use `verificationMs` for independent review and `closingMs` inside the original `totalMs`; defaults remain unchanged. See [human acceptance](acceptance.md) only after independent verification passes. Do not select an old verifier merely because this discovery hint names an old runtime.

Use `.sandcastle/task-config.example.json` as the field source. Supply absolute Linux paths for the prompt, input, authentication, and optional skills. Set `maxIterations` to X and `totalMs` to Y × 60000. Use `.sandcastle/task-progress.md` as the retained progress file. Inspect `.sandcastle/prepare-input.py` before use; it reads GitHub and copies selected materials. Read native dependencies separately because that helper does not collect them.

Keep task inputs outside Git. The prompt must name the mounted issue summary, authorized files or modules, exact acceptance commands, applicable standards, and stop conditions. Require progress updates, explicit task-file staging, a task commit, and a scope check before `<promise>COMPLETE</promise>`. Use task-specific input and prompt files; the checked-in `prompt.md` is the historical #4 prompt. Keep failed-run inputs, prompts and configurations unchanged when preparing a continuation.

For GitHub TLS/HTTP failures, follow the linked issue tracker's session-only HTTP/2 troubleshooting after observing the failure. Preserve existing `GODEBUG` options and restore the prior value afterward. Save complete successful issue body, comments, state and native blocking-dependency responses immediately, before parsing or later preparation, in the task input directory with their command, fetch time and exit status. The selected `prepare-input.py` retains each issue-view attempt under `fetches/` with raw stdout, stderr and a command/time/exit record; native dependencies still require a separate read with the same evidence handling. Reuse those responses during the same preparation; reread when a relevant state change or required freshness check warrants it, retaining earlier attempts. Leave credentials and permanent network settings unchanged.

### Linux-consumed preparation files

Write generated shell scripts and copied runtime source with LF. On Windows Python, use `with destination.open('w', encoding='utf-8', newline='\n') as output: output.write(text.replace('\r\n', '\n'))`; default text writing can restore CRLF even after normalization. Change only the selected preparation copies, preserving host user files. Use ordinary `git diff --check` and inspect generated shell files before invoking them; whitespace checks alone do not prove that an untracked launch script uses LF.

When borrowing an existing Linux `node_modules` via a symlink, exclude that exact untracked link in the selected checkout's local exclude file before checking cleanliness or staging preparation files. Find the file with `git rev-parse --git-path info/exclude` from that checkout, preserving its existing entries; linked worktrees may share the repository exclude file. Use an anchored path such as `/node_modules` for a root-level link, or the actual nested path. Verify with `git check-ignore -v -- node_modules` and `git status --short --untracked-files=all`; substitute the actual link path. An exclude does not hide tracked files: check `git ls-files -- node_modules` before proceeding. Keep the user `.gitignore` and global Git configuration unchanged, and explicitly stage preparation files.

### Commands and paths in the task prompt

When a probe extracts dependencies from a Spring Boot jar, run it after the producing Maven `package` process completes with exit 0 in the same candidate checkout. Await a background build before extraction; the ordinary jar may exist while Boot repackaging is still running. Reuse the recorded build exit and inspect the expected `BOOT-INF/lib` entries. A failed or unfinished build calls for correcting that build, not another parallel package or treating extraction failure as a business result. List dependent commands in order; independent checks may still run concurrently within the authorized budget.

Resolve the WSL Node executable before launch. On this host, `/home/endercloud/.nvm/versions/node/v22.23.3/bin/node --version` was verified on 2026-10-09. Use that absolute executable after confirming it still exists; `wsl --exec` does not load nvm shell initialization. Container Java/Maven checks use `bash -c`, with `command -v java` and `command -v mvn` checked in the selected image; a login shell can replace its tool PATH.

Require the task prompt to name repeated validation runs “检查批次 / check batch”. The host reads orchestration iterations only from result/resource records. Rerunning checks within one iteration does not start or count another iteration; keep the original limits unchanged.

For real SQL/Redis verification, configure `networks` as an explicit list of task-owned Docker network names. The provider and independent verifier both attach to that list. Omit it for the Docker default network; use an internal fixture network plus an explicitly authorized download/model network when required. Inspect fixtures, network isolation and cleanup ownership before launch. Network attachment does not authorize access to an existing database.

Confirm commands against the selected image. Python commands use `python3`; provide the actual check command, not only “check links.” A missing `python` alias when `python3` is available is an invocation error, not a missing capability. Inside the same authorized iteration, the agent may correct that command or a known path and rerun its affected check. The failed check is unresolved until a corrected execution passes. Persistent failures, new dependency requirements, unknown decisions, ownership or authorization problems stop the task. This does not authorize restarting a supervisor that has ended or changing iteration/time limits.

For Python invocation evidence, distinguish the Windows host, WSL host and container. Record each actual executable/entrypoint, argument list, working directory, script location or bind mapping, exit code and sanitized error separately. Resolve the host executable before invoking it; a container's `command -v python3` does not establish the host entrypoint. If a relative invocation fails and a confirmed absolute executable succeeds, retain both attempts and their directories. Without reproduction evidence, report the cause as unconfirmed; success alone does not establish a dependency, PATH or working-directory fix. Apply the existing command-correction and recovery boundaries above.

Supply these locations separately:

- Reader's project root: the directory actually containing `yuweiju-backend`, `yuweiju-web-vue`, and `yuweiju-weixin-miniapp`; Windows onboarding `$RepoRoot` points here.
- Task artifact directory: inputs, configuration and evidence; it is not automatically an application checkout.
- Runtime checkout: `repoRoot` from `task-config.mts`, confirmed with `git rev-parse --show-toplevel`.
- Agent worktree: read the actual resource record after sandbox readiness. The container's `/home/agent/workspace` binds this worktree; the agent can confirm its container root with `pwd` and `git rev-parse --show-toplevel`.
- Independent snapshot: the separate directory exported from the candidate commit for review.

Apply the current project AGENTS output restriction when choosing artifact directories and runtime checkouts. Existing external checkouts may be read as sources; new project artifacts stay under the project, normally `.scratch/`. Do not describe an artifact directory as the bind-mapped source root. If a path is not yet known, leave its mapping pending rather than guessing it.

### Documentation checks

For documentation tasks, use the bundled [relative-link checker](../scripts/check-doc-links.py). It checks inline local Markdown file/image links, skips fenced and inline code, and reports missing files. URI and anchor destinations are excluded; reference-style links and fragment existence still need review. Check documents in a fresh tracked snapshot when validating fresh-clone portability.

Copy the helper to the selected runtime checkout at `.agents/skills/yuweiju-afk/scripts/check-doc-links.py` and include it in the preparation commit before selecting `startCommit`. This makes the same command available to the agent and the independent snapshot; keep it separate from task changes. For issue #3, after both documents exist:

```sh
python3 .agents/skills/yuweiju-afk/scripts/check-doc-links.py --root . docs/agents/local-development.md docs/verification-evidence/2026-10-09/issue3-host-verification.md
```

Use that literal command in the prompt and `checkCommands`; change only the document list for another issue. Run `git diff --check` on the host checkout; a container-mounted Git worktree may point to a host-only `.git` directory. Parse PowerShell examples with the native PowerShell parser without executing startup or original-data commands. Separately verify that the documented root contains the actual application directories: syntax and links alone do not establish a usable working directory.

From Windows, pass Linux tool arguments through `wsl.exe -d Ubuntu --exec` using an argument list. When a shell is required, pass the command as a correctly quoted argument or use a project-local script file. JSON quoting is not shell escaping. Avoid embedding a Markdown regex containing backticks in double-quoted shell code; the bundled script removes that need. Inspect stderr and business results even when a wrapper returns exit 0.

Evidence summaries must identify historical host results, checks actually run by the agent, draft-only independent review, and verification of the exact task commit. Record working directories and exits for each; do not turn a planned or interrupted check into a completed one. A draft check or historical login result alone does not mark the new task complete.

Check that the task branch is absent and the start commit includes the selected runtime and required project code. Inspect changes before forming a preparation commit. Keep preparation changes separate from task changes. If runtime support is missing, use the retained [parameter patch](runtime-parameters.patch) only after checking its applicability. Never patch the old pilot.

## Run and verify

Independent acceptance from Windows uses `.sandcastle/verify-task.ps1`; see [verification entry](../../../../.sandcastle/verification.md) for arguments, shared check configuration and host-only Docker checks. This selects Ubuntu explicitly and resolves Windows worktree Git metadata without changing global Git settings. Keep the direct Python entry below for Linux callers.

From the selected WSL checkout, after task authorization:

```sh
SANDCASTLE_TASK_CONFIG=/absolute/task/config.json /home/endercloud/.nvm/versions/node/v22.23.3/bin/node --import ./node_modules/tsx/dist/loader.mjs .sandcastle/main.ts
python3 .sandcastle/verify-task.py --config /absolute/task/config.json --commit LOCAL_TASK_COMMIT
```

Set a separate `SANDCASTLE_EVIDENCE` directory for the run and review. `checkCommands` run from the new snapshot root. Include dependency preparation needed by that fresh snapshot; worker `installCommands` are not reused by review. Read verification code for mount and service needs. Use isolated services and task-specific checks from the affected client standards. For admin script changes, the existing `verify-admin.py --commit LOCAL_TASK_COMMIT` is also available.

For the project-local WSL checkout, set `TMPDIR` and `npm_config_cache` to project-local `.scratch/` directories before launch. Use the direct Node loader command above: the tsx CLI's Unix socket is unsupported on DrvFS.

Review uses no authentication mount or model. It does not prove business acceptance unless its commands check the issue requirements. Missing required tools or unresolved acceptance criteria block launch. Report the missing item and the action needed to resolve it.
