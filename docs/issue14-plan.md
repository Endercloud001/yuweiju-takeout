# Issue 14 implementation plan

Scope is issue #14 only, starting at bcbaa03be56dbd54d0deaa15374e6e9f354b0353. Native blocker #2 is CLOSED per current authorization. Follow docs/agents/workflow.md, backend/admin/miniapp standards and accepted ADR 0001/0002. No GitHub writes, original database, release, schema change, risk algorithm change, miniapp source restoration or issue19 edits.

## Compatibility and consumers (before implementation)

Keep GET /admin/order/conditionSearch and /admin/order/details/{id}, ApiResult code 1, PageData total/records and existing public fields. Use existing nullable riskScore/riskLevel/riskReasons/modelVersion: missing result or optional lookup/assembly failure returns riskLevel UNAVAILABLE, null score/model, friendly reason. This sentinel is response-only, never persisted or offered as a risk filter. Existing management list currently has no risk UI: add one small risk column; standalone detail maps sentinel to 风险暂不可用. Types accept existing nullable values. List dialog does not render risk today and will receive the same fields. Tokens, VITE_API_BASE, routing and error handling stay intact. Miniapp user orderDetail has no risk consumer; its ownership, amount and image behavior stay unchanged and receive isolated HTTP regression. No migration needed.

## Steps and query semantics

1. Move administrator filtering to named OrdersMapper + bound XML with orders alias o; retain Service parameter rules and BaseMapper detail access.
2. Preserve trimmed substring number/phone, status, inclusive parsed begin/end, total and MP pagination, order_time DESC,id DESC. Risk predicates correlate rr.order_id and MAX evaluated_at to o.id. Existing separate EXISTS predicates mean combined level/score can match different rows tied at latest evaluated_at; retain that behavior. Existing risk enrichment selects evaluated_at DESC,model_version DESC (one lexicographically greatest model on a tie). Filtering may therefore match another tied model; document/test this limitation, do not rewrite algorithm.
3. Catch RuntimeException only around optional risk lookup and risk field assembly. Core order/detail SQL, permissions and risk FILTER SQL fail normally; no unfiltered retry. Missing result is unavailable, never LOW/0.
4. Focused service/UI tests plus a reproducible no-model actual Spring/MyBatis/MySQL HTTP probe on synthetic fixtures. All injected table changes restored in finally; owned fixture rows removed. No original configuration imports.

## Verification

Run mvn -B -f yuweiju-backend/pom.xml package; in admin run npm ci, lint, typecheck, test, build. Host uses fresh candidate /workspace, python3 /workspace/.sandcastle/environment/extract-classpath.py, then bash /environment/issue14-check.sh (optional workspace/environment arguments) on separately owned internal MySQL/Redis Compose network. Probe logs named assertions for filters/correlation/ties/time/count/page/sort/empty, detail, optional failures, risk-filter/core failures, authentication/ownership and user regression. Worker has no Docker socket; real MySQL acceptance remains unverified here if unavailable. Mockito and compile success are not DB evidence.

Historical api-results.json and harness-results.json listed in material-status.json are missing. Existing harness source is historical context, not current runtime evidence; never reconstruct logs or rerun its original writes. Preserve existing image refresh path and amount semantics for this narrowly scoped issue; only isolated tests may execute those reads/writes. Historical image correction belongs to its own issue.

## Rollback

Revert task commit (backend mapper/XML/service, consumers and docs together). No schema/data migration. Rollback restores previous risk exception propagation and unqualified correlation SQL; it cannot undo historical image writes or Redis/file side effects. Probe uses only internal synthetic data and restores its failures; discard isolated environment if interrupted. Preserve all existing safety controls.
