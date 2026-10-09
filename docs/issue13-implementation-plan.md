# Issue #13 implementation plan

Use current mounted issue/standards/ADR 0001 and 0002. Local development and dedicated fixture testing only, maximum two iterations / 30 minutes including preparation. No GitHub writes, push, original database operations, publication or runtime policy changes.

Actual investigation: submit currently performs conversion and risk before cart deletion; Redis GET escapes and saveContext swallows write failures while continuing metrics. Cart rules/transactions are in Controller and existing entries bypass saleability. Address ownership is already checked. Risk scoring already catches its Mapper failures.

1. Catch optional observation failures at observation operation boundaries, stop the operation on failed context/set/counter writes, log unavailable without sensitive payloads. Preserve sequential dedup context behavior and document partial Redis residues/retry limitations.
2. Move touched cart use cases into transactional Service and named Mapper methods, validate saleability for add and submit, retain prices/amount rules. Complete core order/detail/cart writes before optional observations, propagate all core write failures including false results. Keep authentication/address ownership outside optional boundaries.
3. Add focused tests and standalone real Spring/MyBatis/MySQL/Redis probe with triggers, SQL row snapshots, post-write faults, Redis residues/retries, HTTP auth/cart/submit/order/admin reads. Assert proxy and sandcastle_fixture before writes, use only synthetic owned rows/keys; scratch model/upload directories; local map fixture.
4. Execute and repeat exact candidate checks, inspect diff/staging, normal local task commit. Record failures/corrections, no bypass.

Commands from project root:
```sh
npm --prefix yuweiju-web-vue/yuweiju-admin ci
mvn -B -f yuweiju-backend/pom.xml package
bash .sandcastle/environment/issue13-check.sh
(cd yuweiju-web-vue/yuweiju-admin && npm run lint && npm run typecheck && npm run test && npm run build)
```
Repeat npm ci, package, probe and four admin commands on unchanged candidate source. Logs under .scratch/issue13-check; progress untracked .sandcastle/task-progress.md.

Consumers: miniapp authentication/header and submit/cart contracts unchanged; admin token/code/fields unchanged. Neither frontend needs source changes; automated admin checks run. Native miniapp/admin UI and password login unavailable, explicitly unverified. No API/schema/money/business dependency changes. Trigger injections are fixture-only and removed in finally.

Rollback: normal follow-up/revert commit if authorized; never restore over modified files. Source rollback cannot undo allocated SQL IDs, Redis effects or external calls. SQL transactions do not span Redis. No outbox/queue or exactly-once guarantee.
