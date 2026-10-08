# Issue 14: administrator order read compatibility

Supplement to 余味居-管理端接口.html for GET `/admin/order/conditionSearch` and GET `/admin/order/details/{id}`. No endpoint, schema, response envelope, field name/type or authentication change. User `/user/order/orderDetail/{id}` retains ownership checks and existing amount/image behavior.

Existing optional risk fields: `riskScore` (nullable integer), `riskLevel` (nullable string), `riskReasons` (nullable string), `modelVersion` (nullable string). Missing result or failed optional risk lookup/assembly returns:

```json
{"riskScore":null,"riskLevel":"UNAVAILABLE","riskReasons":"风险结果暂不可用，请稍后重试","modelVersion":null}
```

`UNAVAILABLE` is a response sentinel, never a persisted risk level or supported risk filter. Management list and standalone detail show 风险暂不可用; clients must not interpret null or UNAVAILABLE as LOW or score zero. Existing list dialog does not display risk. No miniapp risk consumer exists for these administrator endpoints.

`number`/`phone` retain trimmed substring matching; `status` exact matching. `beginTime`/`endTime` are inclusive, using existing parsing/timezone rules. `riskLevel` is trimmed/uppercased, `minRiskScore` inclusive. Both constrain rows at each order's MAX evaluated_at. Separate predicates retain matches across different tied latest rows. Display lookup retains evaluated_at DESC,model_version DESC, so displayed tied model can differ from the model satisfying a filter. No risk tie algorithm changes.

Total/page use the same predicates, sorted order_time DESC,id DESC. Empty page retains matching total; no matches returns total zero and empty records. SQL uses named Mapper XML and bound parameters. A risk filter SQL failure is a failed ApiResult, with no unfiltered retry. Core order/detail SQL and permission failures remain failures. Optional risk read failures alone preserve the core order response.

Rollback: revert the issue14 task commit and deploy backend/admin together; no data migration. Older code restores prior optional failure propagation and unqualified filter SQL. Revert cannot undo existing image-refresh writes; do not run fault probes against original data. See docs/issue14-plan.md for isolated verification.
