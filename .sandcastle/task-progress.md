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
