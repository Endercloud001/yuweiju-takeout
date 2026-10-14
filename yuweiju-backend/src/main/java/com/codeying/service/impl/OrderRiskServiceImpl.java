package com.codeying.service.impl;

import com.codeying.entity.OrderDetail;
import com.codeying.entity.OrderRiskFeatureSnapshot;
import com.codeying.entity.OrderRiskFeedback;
import com.codeying.entity.OrderRiskModelMeta;
import com.codeying.entity.OrderRiskResult;
import com.codeying.entity.OrderRiskStandardizerMeta;
import com.codeying.entity.Orders;
import com.codeying.mapper.OrderRiskFeatureSnapshotMapper;
import com.codeying.mapper.OrderRiskFeedbackMapper;
import com.codeying.mapper.OrderRiskModelMetaMapper;
import com.codeying.mapper.OrderRiskResultMapper;
import com.codeying.mapper.OrderRiskStandardizerMetaMapper;
import com.codeying.properties.OrderRiskProperties;
import com.codeying.service.OrderRiskService;
import com.codeying.mapper.OrdersMapper;
import com.codeying.mapper.OrderDetailMapper;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.codeying.risk.RiskModelFiles;
import static com.codeying.risk.RiskCalculations.*;
import com.codeying.vo.admin.order.OrderRiskModelMetaVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import smile.base.cart.SplitRule;
import smile.classification.LogisticRegression;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Order risk scoring service implementation.
 *
 * @author Endercloud
 */
@Slf4j
@Service
public class OrderRiskServiceImpl implements OrderRiskService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ZoneOffset APP_OFFSET = ZoneOffset.ofHours(8);
    private static final int RULE_BASE_SCORE = 10;
    private static final String RULE_MODEL_VERSION = "rule-baseline-v1";
    private static final String RULE_BASE_SCORE_KEY = "base_score";
    private static final String RULE_HIGH_FREQ_1H = "high_freq_1h";
    private static final String RULE_AMOUNT_SPIKE_3X = "amount_spike_3x";
    private static final String RULE_SUSPICIOUS_REMARK = "suspicious_remark";
    private static final long MODEL_REFRESH_INTERVAL_SECONDS = 300L;
    private static final long TRAIN_WINDOW_DAYS = 90L;
    private static final long HOLDOUT_DAYS = 30L;
    private static final String MODEL_VERSION_PREFIX = "order-risk";
    private static final String MODEL_STATUS_ACTIVE = "ACTIVE";
    private static final String MODEL_STATUS_REJECTED = "REJECTED";
    private static final String MODEL_STATUS_ARCHIVED = "ARCHIVED";
    private static final DateTimeFormatter MODEL_VERSION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> REMARK_KEYWORDS = List.of(
            "\u4e0d\u8981\u53d1\u8d27",
            "\u6d4b\u8bd5",
            "test"
    );

    private final OrdersMapper ordersMapper;
    private final TransactionTemplate transactions;
    private final TransactionTemplate scoringTransactions;
    private final OrderDetailMapper orderDetailMapper;
    private final OrderRiskFeatureSnapshotMapper orderRiskFeatureSnapshotMapper;
    private final OrderRiskResultMapper orderRiskResultMapper;
    private final OrderRiskModelMetaMapper orderRiskModelMetaMapper;
    private final OrderRiskFeedbackMapper orderRiskFeedbackMapper;
    private final OrderRiskStandardizerMetaMapper orderRiskStandardizerMetaMapper;
    private final OrderRiskProperties orderRiskProperties;
    private final ObjectMapper objectMapper;

    private final AtomicReference<ModelBundle> modelRef = new AtomicReference<>();
    private volatile Instant lastModelRefreshTime = Instant.EPOCH;
    private volatile String modelAvailability = "model_missing";

    public OrderRiskServiceImpl(
            OrdersMapper ordersMapper,
            OrderDetailMapper orderDetailMapper,
            PlatformTransactionManager transactionManager,
            OrderRiskFeatureSnapshotMapper orderRiskFeatureSnapshotMapper,
            OrderRiskResultMapper orderRiskResultMapper,
            OrderRiskModelMetaMapper orderRiskModelMetaMapper,
            OrderRiskFeedbackMapper orderRiskFeedbackMapper,
            OrderRiskStandardizerMetaMapper orderRiskStandardizerMetaMapper,
            OrderRiskProperties orderRiskProperties,
            ObjectMapper objectMapper
    ) {
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.scoringTransactions = new TransactionTemplate(transactionManager);
        // Scoring is optional inside order submission/payment. A savepoint prevents its failure
        // from marking the core order transaction rollback-only, while keeping score writes atomic.
        this.scoringTransactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
        this.orderRiskFeatureSnapshotMapper = orderRiskFeatureSnapshotMapper;
        this.orderRiskResultMapper = orderRiskResultMapper;
        this.orderRiskModelMetaMapper = orderRiskModelMetaMapper;
        this.orderRiskFeedbackMapper = orderRiskFeedbackMapper;
        this.orderRiskStandardizerMetaMapper = orderRiskStandardizerMetaMapper;
        this.orderRiskProperties = orderRiskProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void scoreOrder(Long orderId, String trigger) {
        scoreOrderWithOutcome(orderId, trigger);
    }

    private boolean scoreOrderWithOutcome(Long orderId, String trigger) {
        if (orderId == null || !Boolean.TRUE.equals(orderRiskProperties.getTask().getScoringEnabled())) {
            return false;
        }
        try {
            Orders order = ordersMapper.selectById(orderId);
            if (order == null) {
                return false;
            }

            refreshModelIfNeeded();
            Map<String, Object> rawFeatures = buildRawFeatures(order);
            ScoreDecision scoreDecision = evaluateByModel(order, rawFeatures, trigger);
            if (scoreDecision == null) {
                scoreDecision = evaluateByRule(order, rawFeatures, trigger);
            }
            ScoreDecision persisted = scoreDecision;
            scoringTransactions.executeWithoutResult(tx -> {
                persistFeatureSnapshot(orderId, orderRiskProperties.getFeatureVersion(), rawFeatures);
                persistRiskResult(orderId, persisted);
            });
            log.info(
                    "order_risk_scored orderId={} trigger={} mode={} score={} level={} modelVersion={} ruleHits={}",
                    orderId,
                    trigger,
                    resolveReasonMode(scoreDecision.reasonJson()),
                    scoreDecision.score(),
                    toRiskLevel(scoreDecision.score()),
                    scoreDecision.modelVersion(),
                    toRuleHitsPayload(scoreDecision.ruleHits())
            );
            return true;
        } catch (Exception e) {
            log.error("Order risk scoring failed, orderId={}, causes={}", orderId, safeCauses(e));
            return false;
        }
    }

    @Override
    public Map<Long, OrderRiskResult> findLatestByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> filtered = orderIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (filtered.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderRiskResult> rows = orderRiskResultMapper.findLatestForOrders(filtered);
        Map<Long, OrderRiskResult> result = new HashMap<>();
        for (OrderRiskResult row : rows) {
            if (row == null || row.getOrderId() == null || result.containsKey(row.getOrderId())) {
                continue;
            }
            result.put(row.getOrderId(), row);
        }
        return result;
    }

    @Override
    public String summarizeReason(OrderRiskResult result) {
        if (result == null || !StringUtils.hasText(result.getReasonJson())) {
            return null;
        }
        try {
            Map<?, ?> json = objectMapper.readValue(result.getReasonJson(), Map.class);
            if ("INCOMPLETE".equals(json.get("dataStatus"))) return "特征数据不完整，规则降级：" + json.get("missingFeatures");
            Object mode = json.get("mode");
            if ("rule".equals(mode)) {
                Object hits = json.get("ruleHits");
                if (hits instanceof List<?> list && !list.isEmpty()) {
                    return list.stream().map(String::valueOf).collect(Collectors.joining(","));
                }
                return "rule_fallback";
            }
            Object topFeatures = json.get("topFeatures");
            if (topFeatures instanceof List<?> list && !list.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Object feature : list) {
                    if (feature instanceof Map<?, ?> map && map.get("name") != null) {
                        names.add(String.valueOf(map.get("name")));
                    }
                    if (names.size() >= 3) {
                        break;
                    }
                }
                if (!names.isEmpty()) {
                    return String.join(",", names);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void runScheduledTraining() {
        Map<String, Object> result = runTraining();
        log.info("order_risk_training status={} reason={} modelVersion={}", result.get("status"), result.get("reason"), result.get("modelVersion"));
    }

    @Override
    public synchronized Map<String, Object> runTraining() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "FAILED");
        result.put("reason", "execution_failure");
        result.put("message", "Order risk training failed");
        result.put("trained", false);
        result.put("activated", false);
        result.put("fileSaved", false);
        result.put("metadataSaved", false);
        result.put("executionMode", "SYNCHRONOUS");
        try {
            TrainingPreparation prep = prepareTrainingData();
            result.putAll(readiness(prep));
            String blocked = (String) result.get("blockedReason");
            if (!blocked.isEmpty()) {
                result.put("status", "SKIPPED");
                result.put("reason", blocked);
                result.put("message", "Order risk training skipped: " + blocked);
                return result;
            }
            String version = generateModelVersion();
            result.put("modelVersion", version);
            result.put("artifactPath", RiskModelFiles.resolve(orderRiskProperties.getModelDir(), version + ".ser").toString());
            TrainingArtifacts artifacts = trainAndEvaluate(version, prep.split(), prep.window());
            result.put("trained", true);
            result.put("metrics", artifacts.metrics());
            String path = persistModelBundle(artifacts.bundle());
            result.put("artifactPath", path);
            result.put("fileSaved", true);
            GateDecision gate = evaluateReleaseGate(artifacts.metrics());
            transactions.executeWithoutResult(tx -> {
                persistStandardizerMeta(artifacts.bundle().getFeatureVersion(), artifacts.bundle().getStandardizerMeta());
                if (gate.accepted()) archiveCurrentActiveModel();
                persistModelMeta(version, path, artifacts.metrics(), prep.window(), gate.accepted() ? MODEL_STATUS_ACTIVE : MODEL_STATUS_REJECTED);
            });
            result.put("metadataSaved", true);
            result.put("activated", gate.accepted());
            result.put("status", gate.accepted() ? "TRAINED" : "REJECTED");
            result.put("reason", gate.reason());
            result.put("message", gate.accepted() ? "New order risk model trained and activated" : "New order risk model trained but rejected: " + gate.reason());
            if (gate.accepted()) {
                modelRef.set(artifacts.bundle());
                lastModelRefreshTime = Instant.now();
            }
        } catch (Exception e) {
            boolean fileExists = result.get("artifactPath") instanceof String path && Files.exists(java.nio.file.Path.of(path));
            result.put("artifactExists", fileExists);
            result.put("status", fileExists ? "PARTIAL" : "FAILED");
            // Retain the cause internally; responses never contain dependency messages or credentials.
            log.error("order_risk_training_failed stageOutputs={} causes={}", result.keySet(), safeCauses(e));
        }
        return result;
    }

    @Override
    public Map<String, Object> backfillRecentPaidOrders(Integer days, Integer limit, Boolean onlyMissing) {
        int lookbackDays = clampBackfillDays(days);
        int maxOrders = clampBackfillLimit(limit);
        boolean missingOnly = onlyMissing == null || onlyMissing;
        if (!Boolean.TRUE.equals(orderRiskProperties.getTask().getScoringEnabled())) {
            return Map.of(
                    "triggered", false,
                    "reason", "scoring_disabled",
                    "days", lookbackDays,
                    "limit", maxOrders,
                    "onlyMissing", missingOnly
            );
        }

        Date begin = Date.from(Instant.now().minus(lookbackDays, ChronoUnit.DAYS));
        List<Orders> candidates = ordersMapper.findRiskBackfill(begin, maxOrders);
        if (candidates == null || candidates.isEmpty()) {
            return Map.of(
                    "triggered", true,
                    "days", lookbackDays,
                    "limit", maxOrders,
                    "onlyMissing", missingOnly,
                    "candidateOrders", 0,
                    "processedOrders", 0,
                    "skippedExisting", 0
            );
        }

        List<Long> candidateIds = candidates.stream()
                .map(Orders::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        Set<Long> existing = missingOnly ? loadScoredOrderIds(candidateIds) : Collections.emptySet();
        int skippedExisting = 0;
        int processed = 0;
        int failed = 0;
        for (Long orderId : candidateIds) {
            if (missingOnly && existing.contains(orderId)) {
                skippedExisting++;
                continue;
            }
            if (scoreOrderWithOutcome(orderId, "backfill")) processed++;
            else failed++;
        }

        log.info(
                "order_risk_backfill_done days={} limit={} onlyMissing={} candidateOrders={} processedOrders={} skippedExisting={}",
                lookbackDays,
                maxOrders,
                missingOnly,
                candidateIds.size(),
                processed,
                skippedExisting
        );
        return Map.of(
                "triggered", true,
                "days", lookbackDays,
                "limit", maxOrders,
                "onlyMissing", missingOnly,
                "candidateOrders", candidateIds.size(),
                "processedOrders", processed,
                "failedOrders", failed,
                "status", failed == 0 ? "COMPLETE" : processed == 0 ? "FAILED" : "PARTIAL",
                "skippedExisting", skippedExisting
        );
    }

    @Override
    public Map<String, Object> getTrainingReadiness() {
        return readiness(prepareTrainingData());
    }

    private Map<String, Object> readiness(TrainingPreparation prep) {
        DatasetSplit split = prep.split();
        int minAnomalyLabels = orderRiskProperties.getTraining().getMinAnomalyLabels();
        boolean hasDataset = !split.trainSamples().isEmpty() && !split.holdoutSamples().isEmpty();
        boolean enoughPositive = prep.trainPositiveCount() >= minAnomalyLabels;
        boolean bothClasses = prep.trainPositiveCount() > 0 && prep.trainPositiveCount() < split.trainSamples().size();
        String blockedReason;
        if (prep.windowOrderCount() <= 0) {
            blockedReason = "no_orders_in_window";
        } else if (!hasDataset) {
            blockedReason = "insufficient_dataset";
        } else if (!enoughPositive) {
            blockedReason = "insufficient_positive_labels";
        } else if (!bothClasses) {
            blockedReason = "insufficient_classes";
        } else {
            blockedReason = "";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ready", hasDataset && enoughPositive && bothClasses);
        payload.put("labelPolicy", "latest approve/reject overrides; cancelled=1, other status=0 fallback");
        payload.put("explicitFeedbackOrders", prep.explicitFeedbackCount());
        payload.put("policyDerivedOrders", prep.windowOrderCount() - prep.explicitFeedbackCount());
        payload.put("incompleteOrMissingSnapshots", prep.windowOrderCount() - split.trainSamples().size() - split.holdoutSamples().size());
        payload.put("blockedReason", blockedReason);
        payload.put("featureVersion", orderRiskProperties.getFeatureVersion());
        payload.put("minAnomalyLabels", minAnomalyLabels);
        payload.put("trainWindowStart", prep.window().trainStart());
        payload.put("trainWindowEnd", prep.window().trainEnd());
        payload.put("holdoutWindowStart", prep.window().holdoutStart());
        payload.put("holdoutWindowEnd", prep.window().holdoutEnd());
        payload.put("windowOrders", prep.windowOrderCount());
        payload.put("snapshotOrders", prep.snapshotOrderCount());
        payload.put("trainSamples", split.trainSamples().size());
        payload.put("holdoutSamples", split.holdoutSamples().size());
        payload.put("trainPositives", prep.trainPositiveCount());
        payload.put("holdoutPositives", prep.holdoutPositiveCount());
        return payload;
    }

    @Override
    public List<OrderRiskModelMetaVO> listModelVersions(Integer limit) {
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        List<OrderRiskModelMeta> rows = orderRiskModelMetaMapper.findRecent(size);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrderRiskModelMetaVO> result = new ArrayList<>(rows.size());
        for (OrderRiskModelMeta row : rows) {
            if (row == null) {
                continue;
            }
            OrderRiskModelMetaVO vo = new OrderRiskModelMetaVO();
            vo.setModelVersion(row.getModelVersion());
            vo.setArtifactPath(row.getArtifactPath());
            vo.setStatus(row.getStatus());
            vo.setTrainWindowStart(row.getTrainWindowStart());
            vo.setTrainWindowEnd(row.getTrainWindowEnd());
            vo.setCreatedAt(row.getCreatedAt());
            vo.setMetrics(readJsonMap(row.getMetricsJson()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public Map<String, Object> getReplaySummary(String modelVersion) {
        OrderRiskModelMeta meta = getModelMeta(modelVersion);
        if (meta == null) {
            return Map.of("replayStatus", "MODEL_MISSING", "reason", "model_not_found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> metrics = Collections.emptyMap();
        boolean invalidMetrics = false;
        if (StringUtils.hasText(meta.getMetricsJson())) {
            try {
                metrics = objectMapper.readValue(meta.getMetricsJson(), new TypeReference<Map<String, Object>>() {});
                if (metrics == null) { metrics = Collections.emptyMap(); invalidMetrics = true; }
            } catch (Exception e) {
                invalidMetrics = true;
                log.warn("Order risk replay metrics unreadable, causes={}", safeCauses(e));
            }
        }
        boolean fileExists = StringUtils.hasText(meta.getArtifactPath()) && Files.isRegularFile(RiskModelFiles.resolve(orderRiskProperties.getModelDir(), meta.getArtifactPath()));
        result.put("artifactExists", fileExists);
        result.put("replayStatus", !fileExists ? "MODEL_FILE_MISSING" : invalidMetrics ? "INVALID_METRICS" : metrics.isEmpty() ? "NO_METRICS" : "AVAILABLE");
        result.put("reason", !fileExists ? "model_file_missing" : invalidMetrics ? "invalid_replay_metrics" : metrics.isEmpty() ? "no_replay_metrics" : "stored_holdout_metrics");
        result.put("modelVersion", meta.getModelVersion());
        result.put("status", meta.getStatus());
        result.put("trainWindowStart", meta.getTrainWindowStart());
        result.put("trainWindowEnd", meta.getTrainWindowEnd());
        result.put("createdAt", meta.getCreatedAt());
        result.put("metrics", metrics);
        result.put("artifactPath", meta.getArtifactPath());
        return result;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void submitFeedback(Long orderId, String decision, Long operatorId, String reason) {
        if (orderId == null || operatorId == null || !StringUtils.hasText(decision)) {
            throw new IllegalArgumentException("invalid feedback arguments");
        }
        String normalized = decision.trim().toLowerCase();
        if (!"approve".equals(normalized) && !"reject".equals(normalized) && !"review".equals(normalized)) {
            throw new IllegalArgumentException("unsupported decision");
        }
        if (ordersMapper.selectById(orderId) == null) throw new IllegalArgumentException("order not found");
        OrderRiskFeedback feedback = new OrderRiskFeedback();
        feedback.setOrderId(orderId);
        feedback.setDecision(normalized);
        feedback.setOperatorId(operatorId);
        feedback.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        feedback.setCreatedAt(new Date());
        orderRiskFeedbackMapper.insert(feedback);

        OrderRiskResult latest = orderRiskResultMapper.findLatest(orderId);
        if (latest != null && StringUtils.hasText(latest.getModelVersion())) {
            orderRiskResultMapper.updateDecision(orderId, latest.getModelVersion(), normalized.toUpperCase());
        }
    }

    private ScoreDecision evaluateByModel(Orders order, Map<String, Object> rawFeatures, String trigger) {
        if (!completeFeatures(rawFeatures)) {
            rawFeatures.put("fallbackReason", "incomplete_features");
            return null;
        }
        ModelBundle bundle = modelRef.get();
        if (bundle == null || bundle.getLrModel() == null || bundle.getRfModel() == null) {
            rawFeatures.put("fallbackReason", modelAvailability);
            return null;
        }
        if (!orderRiskProperties.getFeatureVersion().equals(bundle.getFeatureVersion())) {
            rawFeatures.put("fallbackReason", "feature_version_mismatch");
            return null;
        }
        try {
            double[] rawVector = toFeatureVector(rawFeatures);
            double[] lrInput = standardize(rawVector, bundle.getStandardizerMeta());

            double pLr = predictLrProbability(bundle.getLrModel(), lrInput);
            double pRf = predictRfProbability(bundle.getRfModel(), rawVector);
            double fused = fuseProbability(pLr, pRf);
            int score = toRiskScore(fused);

            ScoreDecision ruleDecision = evaluateByRule(order, rawFeatures, trigger);
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("mode", "model");
            reason.put("topFeatures", buildTopFeatures(bundle, lrInput, rawVector));
            reason.put("ruleHits", toRuleHitsPayload(ruleDecision.ruleHits()));
            reason.put("modelVersion", bundle.getModelVersion());
            reason.put("evaluatedAt", OffsetDateTime.now(APP_OFFSET).toString());
            reason.put("trigger", trigger);
        reason.put("dataStatus", rawFeatures.getOrDefault("dataStatus", "COMPLETE"));
        reason.put("missingFeatures", rawFeatures.getOrDefault("missingFeatures", List.of()));
        reason.put("labelStatus", rawFeatures.getOrDefault("labelStatus", "UNLABELLED_CANDIDATE"));
        reason.put("feedbackDecision", rawFeatures.getOrDefault("feedbackDecision", ""));
        if (rawFeatures.containsKey("fallbackReason")) reason.put("fallbackReason", rawFeatures.get("fallbackReason"));
            return new ScoreDecision(
                    score,
                    bundle.getModelVersion(),
                    ruleDecision.ruleHits(),
                    pLr,
                    pRf,
                    reason,
                    bundle.getFeatureVersion()
            );
        } catch (Exception e) {
            log.warn("Model inference failed, orderId={}, fallback to rule score, causes={}", order.getId(), safeCauses(e));
            rawFeatures.put("fallbackReason", "model_inference_failed");
            return null;
        }
    }

    private ScoreDecision evaluateByRule(Orders order, Map<String, Object> rawFeatures, String trigger) {
        int score = RULE_BASE_SCORE;
        Set<String> ruleHits = new LinkedHashSet<>();

        int orderCount1h = asInt(rawFeatures.get("order_count_1h"));
        if (orderCount1h >= 3) {
            score += 40;
            ruleHits.add(RULE_HIGH_FREQ_1H);
        }

        double avgAmount30d = asDouble(rawFeatures.get("avg_amount_30d"));
        double orderAmount = asDouble(rawFeatures.get("order_amount"));
        if (avgAmount30d > 0D && orderAmount > avgAmount30d * 3D) {
            score += 30;
            ruleHits.add(RULE_AMOUNT_SPIKE_3X);
        }

        String remark = order.getRemark() == null ? "" : order.getRemark().toLowerCase();
        if (REMARK_KEYWORDS.stream().anyMatch(remark::contains)) {
            score += 20;
            ruleHits.add(RULE_SUSPICIOUS_REMARK);
        }

        if (ruleHits.isEmpty()) {
            ruleHits.add(RULE_BASE_SCORE_KEY);
        }

        score = Math.max(0, Math.min(100, score));
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("mode", "rule");
        reason.put("topFeatures", List.of());
        reason.put("ruleHits", toRuleHitsPayload(ruleHits));
        reason.put("modelVersion", RULE_MODEL_VERSION);
        reason.put("evaluatedAt", OffsetDateTime.now(APP_OFFSET).toString());
        reason.put("trigger", trigger);
        reason.put("dataStatus", rawFeatures.getOrDefault("dataStatus", "COMPLETE"));
        reason.put("missingFeatures", rawFeatures.getOrDefault("missingFeatures", List.of()));
        reason.put("labelStatus", rawFeatures.getOrDefault("labelStatus", "UNLABELLED_CANDIDATE"));
        reason.put("feedbackDecision", rawFeatures.getOrDefault("feedbackDecision", ""));
        if (rawFeatures.containsKey("fallbackReason")) reason.put("fallbackReason", rawFeatures.get("fallbackReason"));
        return new ScoreDecision(
                score,
                RULE_MODEL_VERSION,
                ruleHits,
                null,
                null,
                reason,
                orderRiskProperties.getFeatureVersion()
        );
    }

    private TrainingArtifacts trainAndEvaluate(String modelVersion, DatasetSplit split, TrainingWindow window) {
        double[][] trainRaw = split.trainSamples().stream().map(Sample::rawVector).toArray(double[][]::new);
        int[] trainLabel = split.trainSamples().stream().mapToInt(Sample::label).toArray();
        Map<String, double[]> standardizer = buildStandardizer(trainRaw);
        double[][] trainStd = applyStandardizer(trainRaw, standardizer);

        LogisticRegression lrModel = LogisticRegression.fit(trainStd, trainLabel, new Properties());
        RandomForest rfModel = trainRandomForest(trainRaw, trainLabel);

        double[] holdoutProba = new double[split.holdoutSamples().size()];
        int[] holdoutLabel = new int[split.holdoutSamples().size()];
        for (int i = 0; i < split.holdoutSamples().size(); i++) {
            Sample sample = split.holdoutSamples().get(i);
            double[] std = standardize(sample.rawVector(), standardizer);
            double pLr = predictLrProbability(lrModel, std);
            double pRf = predictRfProbability(rfModel, sample.rawVector());
            holdoutProba[i] = fuseProbability(pLr, pRf);
            holdoutLabel[i] = sample.label();
        }

        ModelBundle bundle = new ModelBundle();
        bundle.setModelVersion(modelVersion);
        bundle.setFeatureVersion(orderRiskProperties.getFeatureVersion());
        bundle.setLrModel(lrModel);
        bundle.setRfModel(rfModel);
        bundle.setStandardizerMeta(standardizer);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("prAuc", round(computePrAuc(holdoutLabel, holdoutProba), 6));
        metrics.put("recallTop20", round(computeRecallAtTopPercent(holdoutLabel, holdoutProba, 0.20), 6));
        metrics.put("falsePositiveRate", round(computeFalsePositiveRate(holdoutLabel, holdoutProba, 0.50), 6));
        metrics.put("trainSamples", split.trainSamples().size());
        metrics.put("holdoutSamples", split.holdoutSamples().size());
        metrics.put("trainPositives", split.trainSamples().stream().filter(s -> s.label() == 1).count());
        metrics.put("holdoutPositives", split.holdoutSamples().stream().filter(s -> s.label() == 1).count());
        metrics.put("fusedWeights", Map.of(
                "lrWeight", safeWeight(orderRiskProperties.getScoring().getLrWeight(), 0.4),
                "rfWeight", safeWeight(orderRiskProperties.getScoring().getRfWeight(), 0.6)
        ));
        metrics.put("trainWindowStart", OffsetDateTime.ofInstant(window.trainStart().toInstant(), APP_ZONE).toString());
        metrics.put("trainWindowEnd", OffsetDateTime.ofInstant(window.trainEnd().toInstant(), APP_ZONE).toString());
        metrics.put("holdoutWindowStart", OffsetDateTime.ofInstant(window.holdoutStart().toInstant(), APP_ZONE).toString());
        metrics.put("holdoutWindowEnd", OffsetDateTime.ofInstant(window.holdoutEnd().toInstant(), APP_ZONE).toString());
        return new TrainingArtifacts(bundle, metrics);
    }

    private RandomForest trainRandomForest(double[][] trainRaw, int[] trainLabel) {
        DataFrame frame = DataFrame.of(trainRaw, FEATURE_NAMES.toArray(String[]::new));
        frame = frame.add(new IntVector("label", trainLabel));
        int mtry = Math.max(1, (int) Math.sqrt(FEATURE_NAMES.size()));
        int[] classWeight = buildClassWeight(trainLabel);
        return RandomForest.fit(
                Formula.lhs("label"),
                frame,
                200,
                mtry,
                SplitRule.GINI,
                20,
                256,
                5,
                0.8,
                classWeight
        );
    }

    private GateDecision evaluateReleaseGate(Map<String, Object> newMetrics) {
        if (!Boolean.TRUE.equals(orderRiskProperties.getTraining().getRejectIfMetricRegression())) {
            return new GateDecision(true, "disabled");
        }
        OrderRiskModelMeta active = orderRiskModelMetaMapper.findActive();
        if (active == null || !StringUtils.hasText(active.getMetricsJson())) {
            return new GateDecision(true, "no_active_model");
        }

        Map<String, Object> oldMetrics = readJsonMap(active.getMetricsJson());
        double oldPrAuc = asDouble(oldMetrics.get("prAuc"));
        double oldRecall = asDouble(oldMetrics.get("recallTop20"));
        double newPrAuc = asDouble(newMetrics.get("prAuc"));
        double newRecall = asDouble(newMetrics.get("recallTop20"));
        double prAucDelta = safeDouble(orderRiskProperties.getTraining().getRejectThresholds().getPrAucDelta(), 0.02);
        double recallDelta = safeDouble(orderRiskProperties.getTraining().getRejectThresholds().getRecallDelta(), 0.05);

        if (oldPrAuc - newPrAuc >= prAucDelta) {
            return new GateDecision(false, "pr_auc_regression");
        }
        if (oldRecall - newRecall >= recallDelta) {
            return new GateDecision(false, "recall_regression");
        }
        return new GateDecision(true, "pass");
    }

    private void persistModelMeta(String modelVersion, String artifactPath, Map<String, Object> metrics, TrainingWindow window, String status) {
        OrderRiskModelMeta modelMeta = new OrderRiskModelMeta();
        modelMeta.setModelVersion(modelVersion);
        modelMeta.setArtifactPath(artifactPath);
        modelMeta.setMetricsJson(writeJson(metrics));
        modelMeta.setTrainWindowStart(window.trainStart());
        modelMeta.setTrainWindowEnd(window.trainEnd());
        modelMeta.setCreatedAt(new Date());
        modelMeta.setStatus(status);
        orderRiskModelMetaMapper.insert(modelMeta);
    }

    private void archiveCurrentActiveModel() {
        orderRiskModelMetaMapper.archiveActive();
    }

    private String persistModelBundle(ModelBundle bundle) throws Exception {
        return RiskModelFiles.save(orderRiskProperties.getModelDir(), bundle.getModelVersion(), bundle);
    }

    private void persistStandardizerMeta(String featureVersion, Map<String, double[]> standardizer) {
        orderRiskStandardizerMetaMapper.deleteVersion(featureVersion);

        Date now = new Date();
        for (String feature : FEATURE_NAMES) {
            double[] pair = standardizer.get(feature);
            if (pair == null || pair.length < 2) {
                continue;
            }
            OrderRiskStandardizerMeta row = new OrderRiskStandardizerMeta();
            row.setFeatureVersion(featureVersion);
            row.setFeatureName(feature);
            row.setMeanValue(pair[0]);
            row.setStdValue(pair[1]);
            row.setCreatedAt(now);
            orderRiskStandardizerMetaMapper.insert(row);
        }
    }

    private TrainingPreparation prepareTrainingData() {
        TrainingWindow window = buildTrainingWindow();
        List<Orders> windowOrders = listOrders(window.trainStart(), window.holdoutEnd());
        if (windowOrders.isEmpty()) {
            return new TrainingPreparation(window, 0, 0, new DatasetSplit(Collections.emptyList(), Collections.emptyList()), 0L, 0L, 0L);
        }

        Map<Long, Orders> orderMap = windowOrders.stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(Orders::getId, o -> o, (a, b) -> a));
        List<Long> orderIds = new ArrayList<>(orderMap.keySet());
        Map<Long, OrderRiskFeatureSnapshot> snapshotMap = loadFeatureSnapshots(orderIds, orderRiskProperties.getFeatureVersion());
        Map<Long, Integer> labelMap = buildLabels(orderMap, orderIds);
        DatasetSplit split = splitDataset(orderMap, snapshotMap, labelMap, window);
        long trainPositive = split.trainSamples().stream().filter(s -> s.label() == 1).count();
        long holdoutPositive = split.holdoutSamples().stream().filter(s -> s.label() == 1).count();
        return new TrainingPreparation(window, orderMap.size(), snapshotMap.size(), split, trainPositive, holdoutPositive, countExplicitFeedback(orderIds));
    }

    private int clampBackfillDays(Integer days) {
        int configured = orderRiskProperties.getTask().getBackfillDays() == null
                ? 120
                : orderRiskProperties.getTask().getBackfillDays();
        int resolved = days == null ? configured : days;
        return Math.max(1, Math.min(365, resolved));
    }

    private int clampBackfillLimit(Integer limit) {
        int configured = orderRiskProperties.getTask().getBackfillMaxOrders() == null
                ? 5000
                : orderRiskProperties.getTask().getBackfillMaxOrders();
        int resolved = limit == null ? configured : limit;
        return Math.max(1, Math.min(20000, resolved));
    }

    private Set<Long> loadScoredOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> scored = new LinkedHashSet<>();
        for (List<Long> batch : batchIds(orderIds, 500)) {
            List<OrderRiskResult> rows = orderRiskResultMapper.findScored(batch);
            for (OrderRiskResult row : rows) {
                if (row != null && row.getOrderId() != null) {
                    scored.add(row.getOrderId());
                }
            }
        }
        return scored;
    }

    private DatasetSplit splitDataset(
            Map<Long, Orders> orderMap,
            Map<Long, OrderRiskFeatureSnapshot> snapshotMap,
            Map<Long, Integer> labelMap,
            TrainingWindow window
    ) {
        List<Sample> train = new ArrayList<>();
        List<Sample> holdout = new ArrayList<>();
        for (Map.Entry<Long, Orders> entry : orderMap.entrySet()) {
            Long orderId = entry.getKey();
            Orders order = entry.getValue();
            if (order == null || order.getOrderTime() == null) {
                continue;
            }
            OrderRiskFeatureSnapshot snapshot = snapshotMap.get(orderId);
            if (snapshot == null || !StringUtils.hasText(snapshot.getSnapshotJson())) {
                continue;
            }
            Map<String, Object> featureMap = readJsonMap(snapshot.getSnapshotJson());
            if (!completeFeatures(featureMap)) continue;
            double[] raw = toFeatureVector(featureMap);
            int label = labelMap.getOrDefault(orderId, 0);
            Sample sample = new Sample(orderId, order.getOrderTime(), raw, label);
            if (!order.getOrderTime().before(window.holdoutStart())) {
                holdout.add(sample);
            } else {
                train.add(sample);
            }
        }
        return new DatasetSplit(train, holdout);
    }

    private Map<Long, Integer> buildLabels(Map<Long, Orders> orderMap, List<Long> orderIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (orderIds.isEmpty()) {
            return result;
        }

        Map<Long, String> feedbackDecisionMap = new HashMap<>();
        for (List<Long> batch : batchIds(orderIds, 500)) {
            List<OrderRiskFeedback> feedbackRows = orderRiskFeedbackMapper.findForOrders(batch);
            for (OrderRiskFeedback row : feedbackRows) {
                if (row == null || row.getOrderId() == null || !StringUtils.hasText(row.getDecision())) {
                    continue;
                }
                feedbackDecisionMap.putIfAbsent(row.getOrderId(), row.getDecision().trim().toLowerCase());
            }
        }

        for (Long orderId : orderIds) {
            String decision = feedbackDecisionMap.get(orderId);
            if ("reject".equals(decision)) {
                result.put(orderId, 1);
                continue;
            }
            if ("approve".equals(decision)) {
                result.put(orderId, 0);
                continue;
            }
            Orders order = orderMap.get(orderId);
            int fallback = (order != null && order.getStatus() != null && order.getStatus() == Orders.CANCELLED) ? 1 : 0;
            result.put(orderId, fallback);
        }
        return result;
    }

    private Map<Long, OrderRiskFeatureSnapshot> loadFeatureSnapshots(List<Long> orderIds, String featureVersion) {
        Map<Long, OrderRiskFeatureSnapshot> result = new HashMap<>();
        if (orderIds.isEmpty()) {
            return result;
        }
        for (List<Long> batch : batchIds(orderIds, 500)) {
            List<OrderRiskFeatureSnapshot> rows = orderRiskFeatureSnapshotMapper.findForOrders(batch, featureVersion);
            for (OrderRiskFeatureSnapshot row : rows) {
                if (row == null || row.getOrderId() == null || result.containsKey(row.getOrderId())) {
                    continue;
                }
                result.put(row.getOrderId(), row);
            }
        }
        return result;
    }

    private List<Orders> listOrders(Date begin, Date end) {
        return ordersMapper.findRiskWindow(begin, end);
    }

    private TrainingWindow buildTrainingWindow() {
        Instant now = Instant.now();
        Instant holdoutStart = now.minus(HOLDOUT_DAYS, ChronoUnit.DAYS);
        Instant trainStart = holdoutStart.minus(TRAIN_WINDOW_DAYS, ChronoUnit.DAYS);
        return new TrainingWindow(Date.from(trainStart), Date.from(holdoutStart), Date.from(holdoutStart), Date.from(now));
    }

    private String generateModelVersion() {
        return MODEL_VERSION_PREFIX + "-" + MODEL_VERSION_TIME_FORMAT.format(OffsetDateTime.now(APP_OFFSET)) + "-" + java.util.UUID.randomUUID().toString().substring(0,8);
    }

    private void persistFeatureSnapshot(Long orderId, String featureVersion, Map<String, Object> rawFeatures) {
        orderRiskFeatureSnapshotMapper.deleteVersion(orderId, featureVersion);

        OrderRiskFeatureSnapshot snapshot = new OrderRiskFeatureSnapshot();
        snapshot.setOrderId(orderId);
        snapshot.setFeatureVersion(featureVersion);
        snapshot.setSnapshotJson(writeJson(rawFeatures));
        snapshot.setCreatedAt(new Date());
        orderRiskFeatureSnapshotMapper.insert(snapshot);
    }

    private void persistRiskResult(Long orderId, ScoreDecision scoreDecision) {
        OrderRiskResult existing = orderRiskResultMapper.findLatest(orderId);
        orderRiskResultMapper.deleteVersion(orderId, scoreDecision.modelVersion());

        OrderRiskResult row = new OrderRiskResult();
        row.setOrderId(orderId);
        row.setModelVersion(scoreDecision.modelVersion());
        row.setFeatureVersion(scoreDecision.featureVersion());
        row.setRiskScore(scoreDecision.score());
        row.setRiskLevel(toRiskLevel(scoreDecision.score()));
        row.setPLr(scoreDecision.pLr() == null ? null : BigDecimal.valueOf(scoreDecision.pLr()).setScale(6, RoundingMode.HALF_UP));
        row.setPRf(scoreDecision.pRf() == null ? null : BigDecimal.valueOf(scoreDecision.pRf()).setScale(6, RoundingMode.HALF_UP));
        row.setReasonJson(writeJson(scoreDecision.reasonJson()));
        row.setEvaluatedAt(new Date());
        row.setDecisionStatus(existing != null ? existing.getDecisionStatus() : "PENDING");
        String feedback = String.valueOf(scoreDecision.reasonJson().getOrDefault("feedbackDecision", ""));
        if (List.of("approve", "reject", "review").contains(feedback)) row.setDecisionStatus(feedback.toUpperCase());
        if (row.getDecisionStatus() == null) row.setDecisionStatus("PENDING");
        orderRiskResultMapper.insert(row);
    }

    private Map<String, Object> buildRawFeatures(Orders order) {
        Map<String, Object> features = new LinkedHashMap<>();
        Long userId = order.getUserId();
        Date now = order.getOrderTime() == null ? new Date() : order.getOrderTime();
        Date begin1h = Date.from(now.toInstant().minus(1, ChronoUnit.HOURS));
        Date begin24h = Date.from(now.toInstant().minus(24, ChronoUnit.HOURS));
        Date begin30d = Date.from(now.toInstant().minus(30, ChronoUnit.DAYS));

        int count1h = countOrdersByWindow(userId, begin1h, now);
        int count24h = countOrdersByWindow(userId, begin24h, now);
        int count30d = countOrdersByWindow(userId, begin30d, now);
        int cancel30d = countCancelledOrdersByWindow(userId, begin30d, now);
        double cancelRate30d = count30d <= 0 ? 0D : (double) cancel30d / (double) count30d;

        BigDecimal avgAmount30d = avgPaidAmountByWindow(userId, begin30d, now);
        BigDecimal orderAmount = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
        int remarkLength = StringUtils.hasText(order.getRemark()) ? order.getRemark().trim().length() : 0;

        List<OrderDetail> details = orderDetailMapper.findByOrder(order.getId());
        int itemCount = details == null ? 0 : details.stream().mapToInt(d -> d.getNumber() == null ? 0 : d.getNumber()).sum();
        int uniqueDishCount = details == null ? 0 : (int) details.stream()
                .map(d -> d.getDishId() != null ? "D:" + d.getDishId() : "S:" + d.getSetmealId())
                .filter(StringUtils::hasText)
                .distinct()
                .count();

        List<String> missing = new ArrayList<>();
        if (order.getAmount() == null) missing.add("order_amount");
        if (order.getUserId() == null) missing.add("user_id");
        if (order.getOrderTime() == null) missing.add("order_time");
        if (details == null || details.isEmpty() || details.stream().anyMatch(d -> d.getNumber() == null || (d.getDishId() == null && d.getSetmealId() == null))) missing.add("order_details");
        List<OrderRiskFeedback> feedback = orderRiskFeedbackMapper.findForOrders(List.of(order.getId()));
        String decision = feedback.isEmpty() ? "" : feedback.get(0).getDecision();
        features.put("labelStatus", "approve".equals(decision) || "reject".equals(decision) ? "FEEDBACK_LABELLED" : "UNLABELLED_CANDIDATE");
        features.put("feedbackDecision", decision);
        features.put("dataStatus", missing.isEmpty() ? "COMPLETE" : "INCOMPLETE");
        features.put("missingFeatures", missing);
        features.put("order_amount", orderAmount.doubleValue());
        features.put("item_count", itemCount);
        features.put("unique_dish_count", uniqueDishCount);
        features.put("remark_length", remarkLength);
        features.put("order_count_1h", count1h);
        features.put("order_count_24h", count24h);
        features.put("cancel_rate_30d", round(cancelRate30d, 6));
        features.put("avg_amount_30d", avgAmount30d.doubleValue());
        features.put("order_amount_to_avg_ratio_30d", avgAmount30d.compareTo(BigDecimal.ZERO) <= 0
                ? 0D
                : round(orderAmount.divide(avgAmount30d, 6, RoundingMode.HALF_UP).doubleValue(), 6));
        return features;
    }

    private String toRiskLevel(int riskScore) {
        int low = clampThreshold(orderRiskProperties.getThresholds().getLow(), 50);
        int medium = clampThreshold(orderRiskProperties.getThresholds().getMedium(), 75);
        if (riskScore < low) {
            return "LOW";
        }
        if (riskScore < medium) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private int toRiskScore(double probability) {
        return Math.max(0, Math.min(100, (int) Math.round(probability * 100)));
    }

    private int clampThreshold(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(0, Math.min(100, value));
    }

    private int countOrdersByWindow(Long userId, Date begin, Date end) {
        if (userId == null) {
            return 0;
        }
        return Math.toIntExact(ordersMapper.countRiskHistory(userId, begin, end, null));
    }

    private int countCancelledOrdersByWindow(Long userId, Date begin, Date end) {
        return userId == null ? 0 : Math.toIntExact(ordersMapper.countRiskHistory(userId, begin, end, Orders.CANCELLED));
    }

    private BigDecimal avgPaidAmountByWindow(Long userId, Date begin, Date end) {
        return userId == null ? BigDecimal.ZERO : ordersMapper.averageRiskPaidAmount(userId, begin, end);
    }

    private void refreshModelIfNeeded() {
        Instant now = Instant.now();
        if (ChronoUnit.SECONDS.between(lastModelRefreshTime, now) < MODEL_REFRESH_INTERVAL_SECONDS) {
            return;
        }
        lastModelRefreshTime = now;

        OrderRiskModelMeta activeMeta = orderRiskModelMetaMapper.findActive();
        if (activeMeta == null || !StringUtils.hasText(activeMeta.getArtifactPath())) {
            modelRef.set(null);
            modelAvailability = "model_missing";
            return;
        }
        try {
            modelRef.set(RiskModelFiles.load(orderRiskProperties.getModelDir(), activeMeta.getArtifactPath(), ModelBundle.class));
            modelAvailability = "available";
        } catch (Exception e) {
            log.warn("Load order risk model failed, version={}, causes={}", activeMeta.getModelVersion(), safeCauses(e));
            modelRef.set(null);
            modelAvailability = "model_load_failed";
        }
    }

    private boolean completeFeatures(Map<String, Object> features) {
        if ("INCOMPLETE".equals(features.get("dataStatus"))) return false;
        return FEATURE_NAMES.stream().allMatch(name -> features.get(name) instanceof Number n && Double.isFinite(n.doubleValue()));
    }

    private long countExplicitFeedback(List<Long> ids) {
        Map<Long, String> latest = new HashMap<>();
        for (List<Long> batch : batchIds(ids, 500)) {
            for (OrderRiskFeedback row : orderRiskFeedbackMapper.findForOrders(batch)) {
                if (row != null && row.getOrderId() != null && StringUtils.hasText(row.getDecision())) {
                    latest.putIfAbsent(row.getOrderId(), row.getDecision().trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        return latest.values().stream().filter(d -> "approve".equals(d) || "reject".equals(d)).count();
    }

    private double[] toFeatureVector(Map<String, Object> features) {
        double[] vector = new double[FEATURE_NAMES.size()];
        for (int i = 0; i < FEATURE_NAMES.size(); i++) {
            vector[i] = asDouble(features.get(FEATURE_NAMES.get(i)));
        }
        return vector;
    }

    private double predictLrProbability(LogisticRegression model, double[] standardizedVector) {
        double[] posterior = new double[]{0D, 0D};
        model.predict(standardizedVector, posterior);
        return clampProbability(posterior.length > 1 ? posterior[1] : 0D);
    }

    private double predictRfProbability(RandomForest model, double[] rawVector) {
        DataFrame row = DataFrame.of(new double[][]{rawVector}, FEATURE_NAMES.toArray(String[]::new));
        double[] posterior = new double[]{0D, 0D};
        model.predict(row.get(0), posterior);
        return clampProbability(posterior.length > 1 ? posterior[1] : 0D);
    }

    private double fuseProbability(double pLr, double pRf) {
        double lrWeight = safeWeight(orderRiskProperties.getScoring().getLrWeight(), 0.4);
        double rfWeight = safeWeight(orderRiskProperties.getScoring().getRfWeight(), 0.6);
        double weightSum = lrWeight + rfWeight;
        if (weightSum <= 1e-9) {
            return clampProbability((pLr + pRf) / 2D);
        }
        return clampProbability((lrWeight * pLr + rfWeight * pRf) / weightSum);
    }

    private List<Map<String, Object>> buildTopFeatures(ModelBundle bundle, double[] standardizedVector, double[] rawVector) {
        List<Map<String, Object>> features = new ArrayList<>();
        appendLrTopFeatures(features, bundle.getLrModel(), standardizedVector, rawVector, 2);
        appendRfTopFeatures(features, bundle.getRfModel(), rawVector, 2);
        features.sort(Comparator.comparingDouble((Map<String, Object> m) -> Math.abs(asDouble(m.get("contribution")))).reversed());
        if (features.size() > 5) {
            return new ArrayList<>(features.subList(0, 5));
        }
        return features;
    }

    private void appendLrTopFeatures(
            List<Map<String, Object>> output,
            LogisticRegression lrModel,
            double[] standardizedVector,
            double[] rawVector,
            int limit
    ) {
        double[] coefficients = extractLrCoefficients(lrModel);
        if (coefficients.length == 0) {
            return;
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < Math.min(coefficients.length, FEATURE_NAMES.size()); i++) {
            indexes.add(i);
        }
        indexes.sort((a, b) -> Double.compare(Math.abs(coefficients[b] * standardizedVector[b]), Math.abs(coefficients[a] * standardizedVector[a])));
        for (int i = 0; i < Math.min(limit, indexes.size()); i++) {
            int idx = indexes.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", FEATURE_NAMES.get(idx));
            row.put("contribution", round(Math.abs(coefficients[idx] * standardizedVector[idx]), 6));
            row.put("value", round(rawVector[idx], 6));
            row.put("source", "LR");
            output.add(row);
        }
    }

    private void appendRfTopFeatures(List<Map<String, Object>> output, RandomForest rfModel, double[] rawVector, int limit) {
        double[] importance = rfModel.importance();
        if (importance == null || importance.length == 0) {
            return;
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < Math.min(importance.length, FEATURE_NAMES.size()); i++) {
            indexes.add(i);
        }
        indexes.sort((a, b) -> Double.compare(importance[b], importance[a]));
        for (int i = 0; i < Math.min(limit, indexes.size()); i++) {
            int idx = indexes.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", FEATURE_NAMES.get(idx));
            row.put("contribution", round(importance[idx], 6));
            row.put("value", round(rawVector[idx], 6));
            row.put("source", "RF");
            output.add(row);
        }
    }

    private double[] extractLrCoefficients(LogisticRegression lrModel) {
        try {
            Method method = lrModel.getClass().getMethod("coefficients");
            Object value = method.invoke(lrModel);
            if (value instanceof double[] arr) {
                return arr;
            }
        } catch (Exception ignored) {
            log.debug("Unable to read logistic coefficients from class {}", lrModel.getClass().getName());
        }
        return new double[0];
    }

    private double clampProbability(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        if (value < 0D) {
            return 0D;
        }
        if (value > 1D) {
            return 1D;
        }
        return value;
    }

    private double safeWeight(Double value, double defaultValue) {
        if (value == null || value < 0D) {
            return defaultValue;
        }
        return value;
    }

    private double safeDouble(Double value, double defaultValue) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return defaultValue;
        }
        return value;
    }

    private String resolveReasonMode(Map<String, Object> reasonJson) {
        if (reasonJson == null) {
            return "unknown";
        }
        Object mode = reasonJson.get("mode");
        return mode == null ? "unknown" : String.valueOf(mode);
    }

    static List<String> toRuleHitsPayload(Set<String> ruleHits) {
        if (ruleHits == null || ruleHits.isEmpty()) {
            return List.of(RULE_BASE_SCORE_KEY);
        }
        return new ArrayList<>(ruleHits);
    }

    private int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private double asDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0D;
        }
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private String safeCauses(Throwable failure) {
        // Dependency messages can contain SQL values, paths, tokens or credentials.
        // Keep bounded cause types and source locations, following existing observation logging.
        StringBuilder chain = new StringBuilder();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (depth > 0) chain.append(" <- ");
            chain.append(current.getClass().getName());
            if (current.getStackTrace().length > 0) chain.append("@").append(current.getStackTrace()[0]);
            current = current.getCause();
        }
        if (current != null) chain.append(" <- [truncated]");
        return chain.toString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Order risk JSON serialization failed", e);
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private OrderRiskModelMeta getModelMeta(String modelVersion) {
        if (StringUtils.hasText(modelVersion)) return orderRiskModelMetaMapper.findVersion(modelVersion.trim());
        OrderRiskModelMeta active = orderRiskModelMetaMapper.findActive();
        if (active != null) return active;
        List<OrderRiskModelMeta> recent = orderRiskModelMetaMapper.findRecent(1);
        return recent.isEmpty() ? null : recent.get(0);
    }

    private List<List<Long>> batchIds(List<Long> ids, int batchSize) {
        List<List<Long>> batches = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            batches.add(ids.subList(i, end));
        }
        return batches;
    }

    private record ScoreDecision(
            int score,
            String modelVersion,
            Set<String> ruleHits,
            Double pLr,
            Double pRf,
            Map<String, Object> reasonJson,
            String featureVersion
    ) {}

    private record Sample(Long orderId, Date orderTime, double[] rawVector, int label) {}

    private record DatasetSplit(List<Sample> trainSamples, List<Sample> holdoutSamples) {}

    private record TrainingWindow(Date trainStart, Date trainEnd, Date holdoutStart, Date holdoutEnd) {}

    private record TrainingPreparation(
            TrainingWindow window,
            int windowOrderCount,
            int snapshotOrderCount,
            DatasetSplit split,
            long trainPositiveCount,
            long holdoutPositiveCount,
            long explicitFeedbackCount
    ) {}

    private record TrainingArtifacts(ModelBundle bundle, Map<String, Object> metrics) {}

    private record GateDecision(boolean accepted, String reason) {}

    /**
     * In-memory order risk model bundle.
     */
    public static class ModelBundle implements Serializable {
        private static final long serialVersionUID = 1L;

        private String modelVersion;
        private String featureVersion;
        private LogisticRegression lrModel;
        private RandomForest rfModel;
        private Map<String, double[]> standardizerMeta;

        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public String getFeatureVersion() {
            return featureVersion;
        }

        public void setFeatureVersion(String featureVersion) {
            this.featureVersion = featureVersion;
        }

        public LogisticRegression getLrModel() {
            return lrModel;
        }

        public void setLrModel(LogisticRegression lrModel) {
            this.lrModel = lrModel;
        }

        public RandomForest getRfModel() {
            return rfModel;
        }

        public void setRfModel(RandomForest rfModel) {
            this.rfModel = rfModel;
        }

        public Map<String, double[]> getStandardizerMeta() {
            return standardizerMeta;
        }

        public void setStandardizerMeta(Map<String, double[]> standardizerMeta) {
            this.standardizerMeta = standardizerMeta;
        }
    }
}
