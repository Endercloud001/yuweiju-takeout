package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.codeying.service.OrderDetailService;
import com.codeying.service.OrderRiskService;
import com.codeying.service.OrdersService;
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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final List<String> FEATURE_NAMES = List.of(
            "order_amount",
            "item_count",
            "unique_dish_count",
            "remark_length",
            "order_count_1h",
            "order_count_24h",
            "cancel_rate_30d",
            "avg_amount_30d",
            "order_amount_to_avg_ratio_30d"
    );
    private static final List<String> REMARK_KEYWORDS = List.of(
            "\u4e0d\u8981\u53d1\u8d27",
            "\u6d4b\u8bd5",
            "test"
    );

    private final OrdersService ordersService;
    private final OrderDetailService orderDetailService;
    private final OrderRiskFeatureSnapshotMapper orderRiskFeatureSnapshotMapper;
    private final OrderRiskResultMapper orderRiskResultMapper;
    private final OrderRiskModelMetaMapper orderRiskModelMetaMapper;
    private final OrderRiskFeedbackMapper orderRiskFeedbackMapper;
    private final OrderRiskStandardizerMetaMapper orderRiskStandardizerMetaMapper;
    private final OrderRiskProperties orderRiskProperties;
    private final ObjectMapper objectMapper;

    private final AtomicReference<ModelBundle> modelRef = new AtomicReference<>();
    private volatile Instant lastModelRefreshTime = Instant.EPOCH;

    public OrderRiskServiceImpl(
            OrdersService ordersService,
            OrderDetailService orderDetailService,
            OrderRiskFeatureSnapshotMapper orderRiskFeatureSnapshotMapper,
            OrderRiskResultMapper orderRiskResultMapper,
            OrderRiskModelMetaMapper orderRiskModelMetaMapper,
            OrderRiskFeedbackMapper orderRiskFeedbackMapper,
            OrderRiskStandardizerMetaMapper orderRiskStandardizerMetaMapper,
            OrderRiskProperties orderRiskProperties,
            ObjectMapper objectMapper
    ) {
        this.ordersService = ordersService;
        this.orderDetailService = orderDetailService;
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
        if (orderId == null || !Boolean.TRUE.equals(orderRiskProperties.getTask().getScoringEnabled())) {
            return;
        }
        try {
            Orders order = ordersService.getById(orderId);
            if (order == null) {
                return;
            }

            refreshModelIfNeeded();
            Map<String, Object> rawFeatures = buildRawFeatures(order);
            persistFeatureSnapshot(orderId, orderRiskProperties.getFeatureVersion(), rawFeatures);

            ScoreDecision scoreDecision = evaluateByModel(order, rawFeatures, trigger);
            if (scoreDecision == null) {
                scoreDecision = evaluateByRule(order, rawFeatures, trigger);
            }
            persistRiskResult(orderId, scoreDecision);
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
        } catch (Exception e) {
            log.error("Order risk scoring failed, orderId={}, trigger={}", orderId, trigger, e);
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
        QueryWrapper<OrderRiskResult> wrapper = new QueryWrapper<>();
        wrapper.in("order_id", filtered);
        wrapper.orderByDesc("evaluated_at").orderByDesc("model_version");
        List<OrderRiskResult> rows = orderRiskResultMapper.selectList(wrapper);
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
        try {
            refreshModelIfNeeded();
            TrainingPreparation prep = prepareTrainingData();
            TrainingWindow window = prep.window();
            if (prep.windowOrderCount() <= 0) {
                logTrainingSkip(
                        "no_orders_in_window",
                        Map.of(
                                "trainWindowStart", window.trainStart(),
                                "holdoutWindowEnd", window.holdoutEnd(),
                                "windowOrders", prep.windowOrderCount()
                        )
                );
                return;
            }
            DatasetSplit split = prep.split();

            if (split.trainSamples().isEmpty() || split.holdoutSamples().isEmpty()) {
                logTrainingSkip(
                        "insufficient_dataset",
                        Map.of(
                                "windowOrders", prep.windowOrderCount(),
                                "snapshotOrders", prep.snapshotOrderCount(),
                                "trainSamples", split.trainSamples().size(),
                                "holdoutSamples", split.holdoutSamples().size()
                        )
                );
                return;
            }

            long trainPositive = prep.trainPositiveCount();
            if (trainPositive < orderRiskProperties.getTraining().getMinAnomalyLabels()) {
                logTrainingSkip(
                        "insufficient_positive_labels",
                        Map.of(
                                "trainPositive", trainPositive,
                                "minAnomalyLabels", orderRiskProperties.getTraining().getMinAnomalyLabels(),
                                "trainSamples", split.trainSamples().size(),
                                "holdoutSamples", split.holdoutSamples().size()
                        )
                );
                return;
            }

            String modelVersion = generateModelVersion();
            TrainingArtifacts artifacts = trainAndEvaluate(modelVersion, split, window);
            String artifactPath = persistModelBundle(artifacts.bundle());
            persistStandardizerMeta(artifacts.bundle().getFeatureVersion(), artifacts.bundle().getStandardizerMeta());

            GateDecision gateDecision = evaluateReleaseGate(artifacts.metrics());
            if (gateDecision.accepted()) {
                archiveCurrentActiveModel();
                persistModelMeta(modelVersion, artifactPath, artifacts.metrics(), window, MODEL_STATUS_ACTIVE);
                modelRef.set(artifacts.bundle());
                lastModelRefreshTime = Instant.now();
                log.info(
                        "order_risk_model_activated modelVersion={} trainSamples={} holdoutSamples={} metrics={}",
                        modelVersion,
                        split.trainSamples().size(),
                        split.holdoutSamples().size(),
                        artifacts.metrics()
                );
            } else {
                persistModelMeta(modelVersion, artifactPath, artifacts.metrics(), window, MODEL_STATUS_REJECTED);
                log.warn("order_risk_model_rejected modelVersion={} reason={} metrics={}",
                        modelVersion, gateDecision.reason(), artifacts.metrics());
            }
        } catch (Exception e) {
            log.error("Order risk scheduled training failed.", e);
        }
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
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("pay_status", Orders.PAID)
                .ge("order_time", begin)
                .isNotNull("order_time")
                .orderByDesc("order_time")
                .orderByDesc("id")
                .last("limit " + maxOrders);
        List<Orders> candidates = ordersService.list(wrapper);
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
        for (Long orderId : candidateIds) {
            if (missingOnly && existing.contains(orderId)) {
                skippedExisting++;
                continue;
            }
            scoreOrder(orderId, "backfill");
            processed++;
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
                "skippedExisting", skippedExisting
        );
    }

    @Override
    public Map<String, Object> getTrainingReadiness() {
        TrainingPreparation prep = prepareTrainingData();
        DatasetSplit split = prep.split();
        int minAnomalyLabels = orderRiskProperties.getTraining().getMinAnomalyLabels();
        boolean hasDataset = !split.trainSamples().isEmpty() && !split.holdoutSamples().isEmpty();
        boolean enoughPositive = prep.trainPositiveCount() >= minAnomalyLabels;
        String blockedReason;
        if (prep.windowOrderCount() <= 0) {
            blockedReason = "no_orders_in_window";
        } else if (!hasDataset) {
            blockedReason = "insufficient_dataset";
        } else if (!enoughPositive) {
            blockedReason = "insufficient_positive_labels";
        } else {
            blockedReason = "";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ready", hasDataset && enoughPositive);
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
        QueryWrapper<OrderRiskModelMeta> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at").last("limit " + size);
        List<OrderRiskModelMeta> rows = orderRiskModelMetaMapper.selectList(wrapper);
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
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelVersion", meta.getModelVersion());
        result.put("status", meta.getStatus());
        result.put("trainWindowStart", meta.getTrainWindowStart());
        result.put("trainWindowEnd", meta.getTrainWindowEnd());
        result.put("createdAt", meta.getCreatedAt());
        result.put("metrics", readJsonMap(meta.getMetricsJson()));
        result.put("artifactPath", meta.getArtifactPath());
        return result;
    }

    @Override
    public void submitFeedback(Long orderId, String decision, Long operatorId, String reason) {
        if (orderId == null || operatorId == null || !StringUtils.hasText(decision)) {
            throw new IllegalArgumentException("invalid feedback arguments");
        }
        String normalized = decision.trim().toLowerCase();
        if (!"approve".equals(normalized) && !"reject".equals(normalized) && !"review".equals(normalized)) {
            throw new IllegalArgumentException("unsupported decision");
        }
        OrderRiskFeedback feedback = new OrderRiskFeedback();
        feedback.setOrderId(orderId);
        feedback.setDecision(normalized);
        feedback.setOperatorId(operatorId);
        feedback.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        feedback.setCreatedAt(new Date());
        orderRiskFeedbackMapper.insert(feedback);

        QueryWrapper<OrderRiskResult> latestWrapper = new QueryWrapper<>();
        latestWrapper.eq("order_id", orderId).orderByDesc("evaluated_at").last("limit 1");
        OrderRiskResult latest = orderRiskResultMapper.selectOne(latestWrapper);
        if (latest != null && StringUtils.hasText(latest.getModelVersion())) {
            UpdateWrapper<OrderRiskResult> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("order_id", orderId)
                    .eq("model_version", latest.getModelVersion())
                    .set("decision_status", normalized.toUpperCase());
            orderRiskResultMapper.update(null, updateWrapper);
        }
    }

    private ScoreDecision evaluateByModel(Orders order, Map<String, Object> rawFeatures, String trigger) {
        ModelBundle bundle = modelRef.get();
        if (bundle == null || bundle.getLrModel() == null || bundle.getRfModel() == null) {
            return null;
        }
        if (!orderRiskProperties.getFeatureVersion().equals(bundle.getFeatureVersion())) {
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
            log.warn("Model inference failed, orderId={}, fallback to rule score.", order.getId(), e);
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
        QueryWrapper<OrderRiskModelMeta> wrapper = new QueryWrapper<>();
        wrapper.eq("status", MODEL_STATUS_ACTIVE).orderByDesc("created_at").last("limit 1");
        OrderRiskModelMeta active = orderRiskModelMetaMapper.selectOne(wrapper);
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
        UpdateWrapper<OrderRiskModelMeta> wrapper = new UpdateWrapper<>();
        wrapper.eq("status", MODEL_STATUS_ACTIVE).set("status", MODEL_STATUS_ARCHIVED);
        orderRiskModelMetaMapper.update(null, wrapper);
    }

    private String persistModelBundle(ModelBundle bundle) throws Exception {
        Path modelDir = Paths.get(orderRiskProperties.getModelDir()).normalize();
        if (!modelDir.isAbsolute()) {
            modelDir = Paths.get("").toAbsolutePath().resolve(modelDir).normalize();
        }
        Files.createDirectories(modelDir);
        Path target = modelDir.resolve(bundle.getModelVersion() + ".ser").normalize();
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(target.toFile()))) {
            out.writeObject(bundle);
        }
        return target.toString();
    }

    private void persistStandardizerMeta(String featureVersion, Map<String, double[]> standardizer) {
        QueryWrapper<OrderRiskStandardizerMeta> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("feature_version", featureVersion);
        orderRiskStandardizerMetaMapper.delete(deleteWrapper);

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
            return new TrainingPreparation(window, 0, 0, new DatasetSplit(Collections.emptyList(), Collections.emptyList()), 0L, 0L);
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
        return new TrainingPreparation(window, orderMap.size(), snapshotMap.size(), split, trainPositive, holdoutPositive);
    }

    private void logTrainingSkip(String reason, Map<String, Object> details) {
        log.warn("order_risk_training_skipped reason={} details={}", reason, details);
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
            QueryWrapper<OrderRiskResult> wrapper = new QueryWrapper<>();
            wrapper.in("order_id", batch).select("order_id");
            List<OrderRiskResult> rows = orderRiskResultMapper.selectList(wrapper);
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
            QueryWrapper<OrderRiskFeedback> wrapper = new QueryWrapper<>();
            wrapper.in("order_id", batch).orderByDesc("created_at");
            List<OrderRiskFeedback> feedbackRows = orderRiskFeedbackMapper.selectList(wrapper);
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
            QueryWrapper<OrderRiskFeatureSnapshot> wrapper = new QueryWrapper<>();
            wrapper.eq("feature_version", featureVersion).in("order_id", batch);
            List<OrderRiskFeatureSnapshot> rows = orderRiskFeatureSnapshotMapper.selectList(wrapper);
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
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.ge("order_time", begin).lt("order_time", end).isNotNull("order_time");
        return ordersService.list(wrapper);
    }

    private TrainingWindow buildTrainingWindow() {
        Instant now = Instant.now();
        Instant holdoutStart = now.minus(HOLDOUT_DAYS, ChronoUnit.DAYS);
        Instant trainStart = holdoutStart.minus(TRAIN_WINDOW_DAYS, ChronoUnit.DAYS);
        return new TrainingWindow(Date.from(trainStart), Date.from(holdoutStart), Date.from(holdoutStart), Date.from(now));
    }

    private String generateModelVersion() {
        return MODEL_VERSION_PREFIX + "-" + MODEL_VERSION_TIME_FORMAT.format(OffsetDateTime.now(APP_OFFSET));
    }

    private void persistFeatureSnapshot(Long orderId, String featureVersion, Map<String, Object> rawFeatures) {
        QueryWrapper<OrderRiskFeatureSnapshot> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("order_id", orderId).eq("feature_version", featureVersion);
        orderRiskFeatureSnapshotMapper.delete(deleteWrapper);

        OrderRiskFeatureSnapshot snapshot = new OrderRiskFeatureSnapshot();
        snapshot.setOrderId(orderId);
        snapshot.setFeatureVersion(featureVersion);
        snapshot.setSnapshotJson(writeJson(rawFeatures));
        snapshot.setCreatedAt(new Date());
        orderRiskFeatureSnapshotMapper.insert(snapshot);
    }

    private void persistRiskResult(Long orderId, ScoreDecision scoreDecision) {
        QueryWrapper<OrderRiskResult> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("order_id", orderId).eq("model_version", scoreDecision.modelVersion());
        orderRiskResultMapper.delete(deleteWrapper);

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
        row.setDecisionStatus("PENDING");
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

        List<OrderDetail> details = orderDetailService.list(new QueryWrapper<OrderDetail>().eq("order_id", order.getId()));
        int itemCount = details == null ? 0 : details.stream().mapToInt(d -> d.getNumber() == null ? 0 : d.getNumber()).sum();
        int uniqueDishCount = details == null ? 0 : (int) details.stream()
                .map(d -> d.getDishId() != null ? "D:" + d.getDishId() : "S:" + d.getSetmealId())
                .filter(StringUtils::hasText)
                .distinct()
                .count();

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
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).ge("order_time", begin).le("order_time", end);
        return Math.toIntExact(ordersService.count(wrapper));
    }

    private int countCancelledOrdersByWindow(Long userId, Date begin, Date end) {
        if (userId == null) {
            return 0;
        }
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("status", Orders.CANCELLED)
                .ge("order_time", begin)
                .le("order_time", end);
        return Math.toIntExact(ordersService.count(wrapper));
    }

    private BigDecimal avgPaidAmountByWindow(Long userId, Date begin, Date end) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        QueryWrapper<Orders> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("pay_status", Orders.PAID)
                .ge("order_time", begin)
                .le("order_time", end);
        List<Orders> rows = ordersService.list(wrapper);
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = rows.stream()
                .map(Orders::getAmount)
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(rows.size()), 6, RoundingMode.HALF_UP);
    }

    private void refreshModelIfNeeded() {
        Instant now = Instant.now();
        if (ChronoUnit.SECONDS.between(lastModelRefreshTime, now) < MODEL_REFRESH_INTERVAL_SECONDS) {
            return;
        }
        lastModelRefreshTime = now;

        QueryWrapper<OrderRiskModelMeta> wrapper = new QueryWrapper<>();
        wrapper.eq("status", MODEL_STATUS_ACTIVE).orderByDesc("created_at").last("limit 1");
        OrderRiskModelMeta activeMeta = orderRiskModelMetaMapper.selectOne(wrapper);
        if (activeMeta == null || !StringUtils.hasText(activeMeta.getArtifactPath())) {
            modelRef.set(null);
            return;
        }

        Path modelPath = Paths.get(activeMeta.getArtifactPath()).normalize();
        if (!modelPath.isAbsolute()) {
            modelPath = Paths.get(orderRiskProperties.getModelDir()).resolve(modelPath).normalize();
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(modelPath.toFile()))) {
            Object obj = in.readObject();
            if (obj instanceof ModelBundle bundle) {
                modelRef.set(bundle);
                return;
            }
            log.warn("Unsupported order risk model artifact type: {}", obj == null ? "null" : obj.getClass().getName());
            modelRef.set(null);
        } catch (Exception e) {
            log.warn("Load order risk model bundle failed: {}", modelPath, e);
            modelRef.set(null);
        }
    }

    private double[] toFeatureVector(Map<String, Object> features) {
        double[] vector = new double[FEATURE_NAMES.size()];
        for (int i = 0; i < FEATURE_NAMES.size(); i++) {
            vector[i] = asDouble(features.get(FEATURE_NAMES.get(i)));
        }
        return vector;
    }

    private Map<String, double[]> buildStandardizer(double[][] raw) {
        Map<String, double[]> result = new LinkedHashMap<>();
        if (raw.length == 0) {
            for (String feature : FEATURE_NAMES) {
                result.put(feature, new double[]{0D, 1D});
            }
            return result;
        }
        for (int j = 0; j < FEATURE_NAMES.size(); j++) {
            double mean = 0D;
            for (double[] row : raw) {
                mean += row[j];
            }
            mean /= raw.length;
            double variance = 0D;
            for (double[] row : raw) {
                double diff = row[j] - mean;
                variance += diff * diff;
            }
            variance /= raw.length;
            double std = Math.sqrt(variance);
            if (std <= 1e-9) {
                std = 1D;
            }
            result.put(FEATURE_NAMES.get(j), new double[]{mean, std});
        }
        return result;
    }

    private double[][] applyStandardizer(double[][] raw, Map<String, double[]> standardizer) {
        double[][] standardized = new double[raw.length][FEATURE_NAMES.size()];
        for (int i = 0; i < raw.length; i++) {
            standardized[i] = standardize(raw[i], standardizer);
        }
        return standardized;
    }

    private double[] standardize(double[] raw, Map<String, double[]> standardizer) {
        double[] result = new double[FEATURE_NAMES.size()];
        for (int i = 0; i < FEATURE_NAMES.size(); i++) {
            String feature = FEATURE_NAMES.get(i);
            double[] pair = standardizer == null ? null : standardizer.get(feature);
            double mean = pair == null || pair.length < 2 ? 0D : pair[0];
            double std = pair == null || pair.length < 2 ? 1D : pair[1];
            if (std <= 1e-9) {
                std = 1D;
            }
            result[i] = (raw[i] - mean) / std;
        }
        return result;
    }

    private int[] buildClassWeight(int[] labels) {
        int negative = 0;
        int positive = 0;
        for (int label : labels) {
            if (label == 1) {
                positive++;
            } else {
                negative++;
            }
        }
        if (positive <= 0 || negative <= 0) {
            return new int[]{1, 1};
        }
        int positiveWeight = Math.max(1, (int) Math.round((double) negative / (double) positive));
        return new int[]{1, positiveWeight};
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

    private double computePrAuc(int[] labels, double[] probabilities) {
        List<Integer> order = sortIndexesByProbability(probabilities);
        int positives = 0;
        for (int label : labels) {
            if (label == 1) {
                positives++;
            }
        }
        if (positives <= 0) {
            return 0D;
        }
        double sumPrecision = 0D;
        int hitPositive = 0;
        for (int rank = 0; rank < order.size(); rank++) {
            int idx = order.get(rank);
            if (labels[idx] != 1) {
                continue;
            }
            hitPositive++;
            double precision = (double) hitPositive / (double) (rank + 1);
            sumPrecision += precision;
        }
        return sumPrecision / positives;
    }

    private double computeRecallAtTopPercent(int[] labels, double[] probabilities, double topPercent) {
        int positives = 0;
        for (int label : labels) {
            if (label == 1) {
                positives++;
            }
        }
        if (positives <= 0) {
            return 0D;
        }
        int k = Math.max(1, (int) Math.ceil(labels.length * topPercent));
        List<Integer> order = sortIndexesByProbability(probabilities);
        int hitPositive = 0;
        for (int i = 0; i < Math.min(k, order.size()); i++) {
            if (labels[order.get(i)] == 1) {
                hitPositive++;
            }
        }
        return (double) hitPositive / (double) positives;
    }

    private double computeFalsePositiveRate(int[] labels, double[] probabilities, double threshold) {
        int negatives = 0;
        int falsePositive = 0;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == 1) {
                continue;
            }
            negatives++;
            if (probabilities[i] >= threshold) {
                falsePositive++;
            }
        }
        if (negatives <= 0) {
            return 0D;
        }
        return (double) falsePositive / (double) negatives;
    }

    private List<Integer> sortIndexesByProbability(double[] probabilities) {
        List<Integer> indexes = new ArrayList<>(probabilities.length);
        for (int i = 0; i < probabilities.length; i++) {
            indexes.add(i);
        }
        indexes.sort((a, b) -> Double.compare(probabilities[b], probabilities[a]));
        return indexes;
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
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
        QueryWrapper<OrderRiskModelMeta> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(modelVersion)) {
            wrapper.eq("model_version", modelVersion.trim()).last("limit 1");
            return orderRiskModelMetaMapper.selectOne(wrapper);
        }
        wrapper.eq("status", MODEL_STATUS_ACTIVE).orderByDesc("created_at").last("limit 1");
        OrderRiskModelMeta active = orderRiskModelMetaMapper.selectOne(wrapper);
        if (active != null) {
            return active;
        }
        wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at").last("limit 1");
        return orderRiskModelMetaMapper.selectOne(wrapper);
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
            long holdoutPositiveCount
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
