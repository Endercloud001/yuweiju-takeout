# Issue18 compatible risk outcome details

Existing public routes, ApiResult fields, code=1 and existing model status/metric fields remain. `code=1` means the synchronous request produced a result, including a skipped or failed training execution; consumers must inspect `data.status`.

`POST /admin/order-risk/training/run` retains its map and message and adds:

- `status`: TRAINED (new trained/saved/activated model), REJECTED (new trained/saved model rejected by unchanged metric policy), SKIPPED, FAILED, or PARTIAL (file exists but publishing failed).
- `reason`, `trained`, `activated`, `fileSaved` (serialization completed), `metadataSaved`, `executionMode=SYNCHRONOUS`.
- This run's `modelVersion`/`artifactPath` when assigned, metrics when calculated, and readiness/sample/window counts when preparation completed. On failure `artifactExists` distinguishes retained file presence from completed serialization. A version assigned before fitting is an attempted version, not proof of a saved model.
- Skips include no_orders_in_window, insufficient_dataset, insufficient_positive_labels, insufficient_classes. Failures use sanitized execution_failure; sanitized dependency cause types and source locations remain in server error logging. Earlier ACTIVE never sets this run's trained or activated flags.

Readiness retains all fields and adds `explicitFeedbackOrders`, `policyDerivedOrders`, `incompleteOrMissingSnapshots`, `labelPolicy`. Label fallback is unchanged: latest feedback approve/reject overrides; cancelled is positive, other status negative. Review/no feedback remains a candidate, not an adjudicated label. Latest feedback ties resolve by descending ID after created_at.

Replay retains model status (`ACTIVE`, `REJECTED`, `ARCHIVED`) and stored metrics and adds `replayStatus`: MODEL_MISSING, MODEL_FILE_MISSING, NO_METRICS, INVALID_METRICS, AVAILABLE plus reason and artifactExists. AVAILABLE is stored holdout evaluation, not fresh replay execution or proof of real accuracy. Missing metadata has a nonempty status response.

Score reason JSON adds dataStatus, missingFeatures, labelStatus, feedbackDecision and rule fallbackReason. Missing required source values force explicit incomplete rule fallback and exclude that snapshot from training. Missing order never creates a snapshot/score. Nullable historical data/schema remain unchanged. Existing score levels/weights/hits/explanation and admin order UNAVAILABLE behavior remain.

Feedback keeps approve/reject/review and writes feedback plus latest score decision atomically; missing orders are rejected. It does not cancel an order or ban a user. Order rejection remains the separate existing order endpoint and its state checks.

Consumers: admin `api/modules/order.ts`, statistics route and existing order detail/list. Miniapp has no admin risk consumer; normal ordering remains separate. No migration is required. Revert task code to roll back response/UI changes; synthetic fixture records, Redis markers and model files are retained and are not undone by code/SQL rollback.
