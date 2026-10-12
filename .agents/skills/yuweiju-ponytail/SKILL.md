---
name: yuweiju-ponytail
description: Use when closing out a Yuweiju Sandcastle AFK task after its initial checks, before the final successful candidate commit and COMPLETE.
---

# AFK Ponytail closeout

Every new AFK task includes this closeout within its existing coding budget. Load it after implementation and initial task checks pass. This skill does not start a model, install a plugin, or change session authorization.

1. Read [the upstream Ponytail rules](references/ponytail-upstream.md). Apply **full** to this closeout only. The project's confirmed issue, allowed files, layering, security and compatibility requirements remain authoritative.
2. Review all task changes since the supplied `startCommit`: prior task commits, staged and unstaged changes, and task-owned untracked files. Read connected callers, tests and configuration; exclude unrelated user changes. Prior-iteration evidence explains earlier work but does not prove new edits were reviewed.
3. Apply only simplifications with a concrete reason and preserved behavior, inside the authorized scope. Keep validation, ownership/authentication, transactions, error handling and requested behavior. A documentation-only task checks redundancy, accuracy and links; it does not invent code changes. No worthwhile simplification is a valid result. A scope-external finding is recorded; an unresolved issue that prevents current acceptance stops the task.
4. Record the review scope, adopted/rejected findings with reasons, changed files, check commands/results and unfinished items in the configured progress file. For example: “Ponytail: reviewed task diff and callers; merged duplicate helper; reran the affected checks; permission checks retained.” Use the task's selected evidence paths, keeping logs free of credentials and business payloads.
5. After changes, rerun affected checks and every required task acceptance check. Reuse earlier checks only when this closeout changed nothing and their checked code is identical; cite their evidence. If later edits change the candidate, review those edits and repeat affected checks before completion.
6. End Ponytail mode explicitly with `stop ponytail` / `normal mode`, then follow the task's explicit staging, commit and scope checks. COMPLETE requires this review, passed final checks and a task commit; host independent verification still targets the exact final commit.

Check the existing deadline and `/afk-evidence/closing.json` between work steps. If review or final checks cannot finish within the original budget, preserve the candidate commit, remaining changes, progress and blockers, then leave the task incomplete. Candidate-saving commits remain allowed before closeout finishes; they are recovery artifacts, not evidence of successful completion. Do not extend the budget or relaunch an ended run.

Upstream provenance and [MIT license](LICENSE) are included with the reference. The instructions are read as text from task-input; slash-command discovery, plugin hooks and user configuration are not required.
