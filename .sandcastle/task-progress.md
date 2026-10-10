# Issue #7 progress — retry1

- Started 2026-10-09; authorized ceiling 30 minutes / 2 supervisor iterations, no extra coding agents.
- Issue/dependencies and current workflow/three-client standards/ADRs/backend skill read. Native #2 closed; historical dependency 6 is not native #6.
- Initial worktree clean. Previous pre-coding network failure had zero task commits; no earlier artifacts removed.
- Plan written before coding: docs/issue7-plan.md.
- Historical docs/verification-evidence/2026-10-05 JSON absent; real Wechat and real maintainer PAGE login remain manual acceptance items.
- Java/Maven installed outside PATH at /opt/java/openjdk/bin and /usr/share/maven/bin; use those normal tools. No Git guard rejection.
- In progress: call-chain/legacy audit, implementation and isolated checks. No push, publication or original-data operations.

- Implementation: login orchestration moved into UserService, identity/employee/legacy filters into named Mapper APIs; report/workspace user-count callers use named user methods. Primary-key CRUD and existing plaintext/token/dev fallback semantics preserved.
- Wechat upstream failure sanitization retains cause but avoids credential-bearing URL/body logs; failure code remains 0.
- Initial unit failure was a test assumption (business code is 0; HTTP auth status is 401), corrected. Second run exposed missing inherited request injection in standalone legacy test, corrected fixture wiring. Both logs preserved in .scratch/issue7; these are test corrections, not supervisor iterations.
- Admin npm ci/lint/typecheck/test/build individually completed exit 0. Vitest 2/2 tests passed. No dependency/lockfile changes.
- Isolated probe and synthetic PAGE login script written; awaiting final jar. No host ports published, original writes or Git guard rejection.
- Audit gaps preserved: legacy public registration/session vs admin JWT mismatch; no per-role employee policy or state revalidation of an existing JWT. Isolated schema lacks tb_admin; legacy SQL/template usability will not be claimed.

- Maven final package completed exit 0 (2026-10-09 13:02:57 UTC): 34 tests, 0 failures/errors/skips. Logs: .scratch/issue7/maven-final.log.
- First real probe reached valid/wrong/disabled SQL/HTTP login, filter/sort/empty/status/permissions and user isolation, then failed its narrow wall-clock registration-time assumption. Cleanup ran for owned fixtures. Preserve probe-first.log/check-first.log; rerun uses persisted DATETIME reference and compares named counts against direct bound SQL, without changing product time behavior.
- All artifacts/logs/probe sources reside in worktree/.scratch. No original data, publication, forced Git operations, security removal or extra agents.

- Final real isolated check `.sandcastle/environment/issue7-check.sh --browser` exit 0 (13:06 UTC). Real SQL/Spring/HTTP/Redis and exact enabled legacy route/JWT assertions passed. Synthetic credentials entered via actual Vue PAGE and dashboard screenshot inspected. Probe closed its app/Vite and removed owned DB/cache fixtures and browser credential file.
- CaptchaServlet runtime registration has empty mappings; no legacy captcha endpoint/template usability claim. Late curl after shutdown returned HTTP 000; no endpoint evidence claimed.
- docs/issue7-auth-audit.md records file/line coverage, tests, preserved failures, legacy/schema/auth gaps and human acceptance steps. Real maintainer PAGE / real Wechat remain pending, historical JSON absent.
- Scope/diff --check reviewed; preparing explicit task-only staging and normal local commit. No Git guard rejection; no supervisor continuation used.

- Delivery state: local candidate only; all required automated commands and synthetic PAGE/isolated checks passed. Explicit 26-file task scope staged and cached diff --check passed. Normal task commit recorded in Git metadata; no push or publication. Human real PAGE/real Wechat acceptance and legacy usability remain pending; issue #7 is not fully accepted. Logs and first failures preserved under .scratch/issue7. Supervisor continuations: 0, extra coding agents: 0.

- Current mounted retry verification: initial Git inspection found existing candidate commits ce76fd4 and fff5217 (clean worktree), contradicting the supplied zero-commit description. Preserve them. Ignored .scratch/issue7 artifacts are absent here, so prior report is historical evidence only; required commands and isolated probe will be rerun locally. Plan updated first; no Git guard rejection or supervisor iteration.

- Review found a concrete legacy pagination compatibility regression: Page constructor normalizes nonpositive requested pages while original setters preserve them for PagerFooterVO. Restored setter construction in AdminServiceImpl and added a footer behavior test; no role/auth/schema change. Current build may have compiled pre-fix sources, so final Maven package must rerun after completion.

- Current rerun: npm ci/lint/typecheck/test each exit 0 (Vitest 2/2); no lockfile/frontend edits. Initial Maven package exit 0 with 34 tests at 13:16:27 UTC, but compiled before pagination fix. Final post-fix package and npm build now running; no final result claimed yet. Logs use *-current filenames under .scratch/issue7.

Host handoff: supervisor exhausted its authorized 30-minute total across 2 iterations, without COMPLETE. Pagination compatibility correction was preserved uncommitted. Host independently ran LegacyAdminBehaviorTest in a no-auth/no-model container: exit 0. This correction is committed for exact-candidate independent verification; full acceptance remains pending. Earlier ignored first-iteration logs did not survive framework worktree recreation; historical results are not substituted for new validation.


---

# Issue #8 progress
- Iteration 1/2; deadline 2026-10-10T06:04:35.862Z, unchanged limits.
- Confirmed /home/agent/workspace, HEAD e6d62a7379b505713f182caafa45e14ed3c65fcb, clean worktree.
- Read issue/comments/state/blocker, skills and current standards/decisions/consumer chains.
- Prior /afk-evidence contains this iteration start only; closing.json absent.
- Reviewable plan saved before business edits.
- Historical raw evidence gap authorized for documentation; new isolated verification required.
- Candidate/checks: pending. No external publication or original data writes.
- Business groups Category/Dish/flavor/Setmeal/details migrated to named Mapper conditions through named Service methods; primary-key CRUD and current rules preserved, catches retain cause.
- Check batch 1 underway: Maven package (dependency installation included), npm ci. Raw outputs .scratch/issue8.
- Ordinary CatalogRulesTest added for applicable category associations, mixed-batch refusals, invalid pagination and exception cause (does not claim SQL rollback).
- New Issue8Probe/issue8-check.sh/issue8-browser.cjs implemented: actual context/SQL/Redis/authenticated HTTP, proxy-trigger rollback, owned fixture cleanup and unchanged preexisting catalog rows. Awaiting execution.
- npm ci completed exit 0; its initial output path was inside project root, moved to .scratch/issue8/npm-ci-batch1.log. No dependency/lockfile modifications.
- npm typecheck exit 0; lint/test/build running as independent checks in batch 1.
- Check batch 1 frontend: npm ci/lint/typecheck/test/build all exit 0; Vitest 3 assertions in 2 files. Unchanged frontend source/lockfiles. Maven package still finishing initial dependency downloads.
- Maven batch 1 package exit 0 (05:13:36 UTC), tests included. Source formatting/import cleanup occurred during this build; batch 2 runs final package to bind checks to final Java sources before isolated probe. No test failures or skipped-test substitution.
- Reviewed side effects of new acceptance: root logger off to omit payloads/tokens; dev mock_ login bypasses real WeChat; no maps/weather/OSS/payment/model requests. Existing customer reply task only visits empty process-local locks, analysis/order-risk tasks disabled. Probe binds only owned IDs in task triggers and prepared row operations.
- Check batch 2 final Maven package exit 0 at 05:17:49 UTC: 54 tests, 0 failures/errors/skips, includes 4 CatalogRulesTest cases. Final Java sources compiled. New real isolated/browser check now starts.
- Batch 2 first real probe exit 1 at public user catalog auth expectation before product writes. CURRENT WebMvcConfiguration explicitly excludes all four catalog reads from JWT; expectation corrected to preserve anonymous results. No authentication changes/unknown decisions. Protected user boundary plus admin directory verify missing/wrong-scope token rejection. This is the first failure, not a persistent failure or new supervisor run.
- Preserved probe-batch2-first.log and scenario-results-batch2-first.json. First finally confirmed no triggers/owned user/key and all five preexisting catalog tables unchanged. Same iteration retry now runs; limits unchanged.
- Batch 2 rerun real business groups now passed: Category filters/page/order/enablement/association; Dish+flavor sellable lists/admin roundtrip/status/filters; Setmeal+details roundtrip/status/mixed-sale batch refusal; suspicious bound SQL/empty pages.
- Actual Spring-proxied MySQL trigger rollback assertions passed for second flavor insert and second setmeal-detail insert: entire root and former association records restored, independent product unchanged, both triggers removed. Browser and final own-only cleanup still pending.
- Batch 2 rerun exact issue8-check.sh --browser exit 0 ~05:21 UTC. scenario-results.json: 158 recorded assertions, businessPassed/cleanupPassed true; browser-results.json 4/4 (actual normal login and category/dish/setmeal view search rendering). All three screenshots inspected.
- Final finally removed owned rows/user/key, confirmed no task triggers; sentinel root/flavors preserved before cleanup and all preexisting catalog tables unchanged. Existing sandbox_admin was reused without edits. Probe and Vite processes ended. Isolated autoincrement IDs consumed; no original-data/external business calls, no flush/truncate/schema edits, no models trained.
- All exact required checks passed in check batches; failures and corrections retained. Scope/whitespace checked. No blockers, unresolved decisions or unfinished automated acceptance. Host independent verification and maintainer ALL human acceptance still pending; no publication authorized/performed.
- Preparing explicit task-only local business commit; .scratch/issue8 raw logs/results retained untracked, never staged wholesale. Deadline/iteration limits unchanged, closing.json absent.
