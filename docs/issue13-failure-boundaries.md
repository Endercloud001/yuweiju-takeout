# Issue #13 failure boundaries

The mounted issue/plan and current backend/admin/miniapp standards govern this task. No API/schema/amount change. Backend only; both clients keep their public contracts. Historical harness source/report are background; cleaned logs cannot prove this candidate and the original-data harness was not run.

## Code boundaries

`UserShoppingCartController` resolves the authenticated identity; `ShoppingCartService` owns add/sub/list/clean rules and add/sub transactions. `ShoppingCartMapper` provides named user/item queries and scoped deletion. Adding an existing item and submitting a cart both check current dish/setmeal saleability. Address primary-key read is followed by ownership validation inside submit. Authentication, ownership, saleability, core SQL and the map ETA call are outside optional observation catches.

`OrdersApplicationService.submit` remains a real Spring transaction: save order, save all details, delete this user's cart, then optional conversion and existing optional scoring. Failed save results and unexpected delete counts fail the transaction. Order submit returns a friendly failure instead of exposing SQL details. No automatic business retry is introduced.

`AnalysisObservationServiceImpl` catches runtime failures around one complete exposure/click/conversion/logging operation. Context serialization/write failures now throw internally, stopping subsequent set/counter writes. Metric expiry failure also stops processing. The caller's business operation may succeed while observation is unavailable; no recommendation success receipt is invented. Read-only summary errors still propagate to their own caller, never substitute a fabricated successful summary.

SQL rollback covers orders/details/cart on the same transaction, not Redis, auto-increment allocation, files or external HTTP. Core failure is tested before statistics. A database commit failure occurring after optional Redis writes remains a possible residue; this probe does not emulate lost commit acknowledgements. Map dependency failure remains core ETA failure and occurs before writes; external map is replaced by a local deterministic fixture for tests. No remote AI/map/OSS request, model training or upload is exercised.

## Reproduction and fresh evidence

Run from project root `/home/agent/workspace`:

```sh
npm --prefix yuweiju-web-vue/yuweiju-admin ci
mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue13-check.sh
(cd yuweiju-web-vue/yuweiju-admin && npm run lint && npm run typecheck && npm run test && npm run build)
```

Use a non-login shell retaining the development container's Java/Maven PATH. Initial package failed with exit 127 in a login shell; corrected without downloading tools or changing runtime policy. Probe uses absolute standalone config with dev,afk profiles and the mysql/redis dedicated fixtures. It verifies `SELECT DATABASE() = sandcastle_fixture` and actual orders/cart AOP proxies before writes; a MyBatis interceptor records actual trigger-induced Mapper exceptions. Runtime jar libraries, classes, model/output directories and logs stay under `.scratch/issue13-check`. JWTs exist only in memory and are never recorded.

Each probe run owns distinct synthetic users/address/dish/order/cart rows and model-specific Redis keys. Trigger names are issue-specific and removed in finally. Cleanup deletes only owned synthetic rows/keys; no FLUSH, TRUNCATE, original database access or global clean. SQL snapshots compare full owned order/detail/cart rows including another user's cart, not just counts. Redis evidence records actual value/set membership and TTL at failure and retry; TTL naturally decreases and is not a rollback criterion.

## Scenario results

First run: all 43 real probe scenarios PASS, exit 0. Evidence: `.scratch/issue13-check/probe-results-939029580.json`, `probe-1.log`; package `package-1-corrected.log` exit 0, 21 tests / zero failures, errors or skips. Admin `admin-1.log`: lint/typecheck/test/build exit 0, two Vitest tests. Initial `npm-ci-1.log` exit 0. Second unchanged-candidate package/probe also exit 0: `package-2.log` (21 tests / zero failures, errors or skips), `probe-2.log`, `probe-results-939262595.json` (43 PASS). Second admin `npm-ci-2.log` and `admin-2.log` also exit 0: lint/typecheck/test/build, two Vitest tests. All required automated acceptance commands passed in both runs. These are separate command runs in the provided development container, using dedicated storage and its Maven dependency cache, not a claim of a new clean container.

| Real probe scenarios | Business result |
| --- | --- |
| Exposure/click/conversion: context SET before/after, SADD before/after, INCR before/after, expiry false (21 cases), each followed by same-event retry | Optional method returns normally; failed write stops later counters; actual context/set/counter/TTL and retry values recorded |
| Submit: Redis context GET failure, six write before/after failures, no context (8 cases) | HTTP code 1; actual order amount 36, detail quantity 2 × unit 18; submitting user's cart empty, other user's retained |
| Cart: GET and six write before/after failures (7 cases) | HTTP code 1; owned cart increments from 2 to 3; other user retained |
| Order INSERT, detail INSERT and cart DELETE trigger failures (3 cases) | HTTP failure; actual target Mapper exception recorded, full owned SQL rows and other cart unchanged, Redis values unchanged |
| Authorization/address/saleability | Unauthenticated HTTP and foreign address rejected; stopped-sale submit and existing-item add rejected; core SQL unchanged |
| Risk snapshot Mapper trigger failure | Real SQL exception observed; order/details/cart business commits; no risk snapshot falsely claimed |
| HTTP normal roundtrip | Cart add/sub/list, submit, user order detail, admin order detail code 1 using fixture JWT |
| Isolation/proxies | Actual MySQL `sandcastle_fixture`, Redis operations and Spring AOP orders/cart proxies |

Actual Redis residues for user `939029580`, model `issue13-939029580`, observation date `2026-10-09`: context keys use `yuweiju:analysis:ai:context:<user>`, metric keys use `exposed`/`clicked`/`converted` and their `:count:` counterparts. JSON stores every exact key/value/TTL, including unchanged baseline exposure/click data. `absent` has Redis TTL -2; retained keys with no expiry have TTL -1. Normal context TTL is about 86400 seconds; metric TTL is about 3196800 seconds (37 days).

| Injected boundary | Actual residues after failure | Actual same-event retry |
| --- | --- | --- |
| Context SET before | New exposure context absent; click/conversion retain prior context; no new metric set/counter | Each can subsequently record once, count 1 |
| Context SET after | Context is written; click has clickedDishIds=[939029580], conversion has convertedOrderIds=[123]; respective metric set/count absent | Exposure records count 1; click/conversion return through dedup, metric omissions remain |
| SADD before | Updated context remains; new metric set/count absent | Exposure records count 1; click/conversion skip, omissions remain |
| SADD after | Updated context plus set containing 939029580 remain; set TTL -1, count absent | Exposure repairs expiry and records count 1; click/conversion skip and leave set TTL -1/count absent |
| INCR before | Context and set remain (set TTL about 3196800); count absent | Exposure records count 1; click/conversion skip, missing count remains |
| INCR after | Context/set remain; count value 1 has TTL -1 | Exposure increments to 2 and applies expiry; click/conversion skip, count remains 1 with TTL -1 |
| Set expiry returns false | Context and member set remain; set TTL -1, count absent | Exposure applies expiry/count 1; click/conversion skip, missing expiry/count remain |

No automatic observation retry was added. Dedup is bounded to the current 24-hour context and sequential same dish/order calls; exposure itself has no event dedup, and a later exposure replaces click/conversion context. Concurrent read-modify-write, context expiry/replacement, a new order ID on business retry and lost acknowledgements remain duplication/loss risks. We do not claim Redis rollback, durable reconciliation or exactly-once delivery. Existing residues are not silently cleaned by business code; only this run's synthetic keys are removed by probe cleanup.

## Limits

Native WeChat compilation/checkout UI and native admin UI checks are unavailable and unverified. Generated fixture JWTs prove HTTP authorization, not password login or native interaction. Probe results are synthetic failure-boundary evidence, not production data or model accuracy. Sequential context dedup is not concurrent or durable exactly-once delivery. The risk probe fails the first optional snapshot INSERT; it does not prove snapshot/result atomicity. Existing scoring catches later SQL failures too, so an earlier optional risk row can remain in a successful core transaction. No retry/reconciliation/outbox was added. Source rollback cannot undo Redis or external side effects or SQL sequence gaps.
