# Sandcastle retrospective improvements

This authorized update runs no coding agent or model. The accepted #4 fix and
root follow-up commit 7767930e278ba2fc93204cdf9b1277431069cabf are now included in
the preparation branch; original task branch/results/progress remain intact.

1. Summary/prompt require a task commit and scope/status inspection before completion.
   A marker without commits is incomplete-missing-task-commit; the worker exits
   nonzero without an automatic retry or host commit.
2. The behavior check requires Linux, rejects native Windows nonzero with a clear
   diagnostic, and never skips permission cases. The root package exposes test:refimg
   beside Vitest; management standards describe current paths and checks.
3. verify-admin.py/sh reuse the explicit sh entrypoint and non-root image. Review
   creates a fresh detached commit worktree or uses a labelled isolated snapshot
   beneath evidence. Fresh npm ci: 5 minutes; complete review: 30 minutes.
4. Individual exits/full logs/resource records use the existing evidence root.
   Success, lint exit 7 and SIGTERM were exercised with no model/auth mount;
   owned containers are removed while snapshots and logs remain.
5. README places both test entries together and documents verification/recovery.
   The already accepted #4 entry must not automatically rerun.

Verified: TypeScript, Python compilation, shell syntax, saved real result replays
(committed smoke / uncommitted AFK / absent marker), fresh non-root Linux npm ci,
lint, typecheck, Vitest, build and behavior checks. Four behavior tests cover all
six matrix groups with zero skips. Native Windows npm test:refimg rejects the
permission environment as intended; this is not Windows behavior acceptance.

Evidence: retro-review-console.log, retro-exit7-console.log, retro-cancel-console.log,
retro-cancel-verdict.json, retro-result-summary-check.log, retro-typecheck.log and
per-review admin-review-*-logs in the existing evidence directory. Build chunk
warnings and dependency audit findings remain; dependency versions did not change.

Shared common/main/launch/resource/smoke/config/Dockerfile remain byte-identical to
the previous preparation commit. No full eight-scenario rerun because shared
cancellation/cleanup did not change. The new review entry's own success/failure/
signal cleanup paths were checked. Corrected validation fixture naming and used
an absolute installed Node path for host checks when a non-login environment
lacked the user-local Node PATH; no tool reinstall or configuration change.

No hook, gate, cache, hash, baseline, auth change, GitHub write, database operation
or publication. Root user changes remain. Static summary deploys after verification.
Future tasks require new explicit scope/branch/start-commit authorization. These
checks verify preparation improvements, not another AFK run.
