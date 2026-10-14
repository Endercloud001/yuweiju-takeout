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
import com.codeying.mapper.OrdersMapper;
import com.codeying.mapper.OrderDetailMapper;
import org.springframework.transaction.PlatformTransactionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import smile.classification.LogisticRegression;
import smile.classification.RandomForest;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class OrderRiskServiceIntegrationTest {

    private final OrdersMapper ordersService = Mockito.mock(OrdersMapper.class);
    private final OrderDetailMapper orderDetailService = Mockito.mock(OrderDetailMapper.class);
    private final OrderRiskFeatureSnapshotMapper featureSnapshotMapper = Mockito.mock(OrderRiskFeatureSnapshotMapper.class, Mockito.CALLS_REAL_METHODS);
    private final OrderRiskResultMapper riskResultMapper = Mockito.mock(OrderRiskResultMapper.class, Mockito.CALLS_REAL_METHODS);
    private final OrderRiskModelMetaMapper modelMetaMapper = Mockito.mock(OrderRiskModelMetaMapper.class, Mockito.CALLS_REAL_METHODS);
    private final OrderRiskFeedbackMapper feedbackMapper = Mockito.mock(OrderRiskFeedbackMapper.class, Mockito.CALLS_REAL_METHODS);
    private final OrderRiskStandardizerMetaMapper standardizerMetaMapper = Mockito.mock(OrderRiskStandardizerMetaMapper.class, Mockito.CALLS_REAL_METHODS);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderRiskProperties properties = new OrderRiskProperties();

    @TempDir
    Path tempDir;

    private OrderRiskServiceImpl service;

    @BeforeEach
    void setUp() {
        properties.setModelDir(tempDir.toString());
        Mockito.when(ordersService.averageRiskPaidAmount(any(),any(),any())).thenReturn(BigDecimal.ZERO);
        properties.getTraining().setMinAnomalyLabels(1);
        service = new OrderRiskServiceImpl(
                ordersService,
                orderDetailService,
                Mockito.mock(PlatformTransactionManager.class),
                featureSnapshotMapper,
                riskResultMapper,
                modelMetaMapper,
                feedbackMapper,
                standardizerMetaMapper,
                properties,
                objectMapper
        );

        Mockito.when(featureSnapshotMapper.delete(any(QueryWrapper.class))).thenReturn(1);
        Mockito.when(featureSnapshotMapper.insert(any(OrderRiskFeatureSnapshot.class))).thenReturn(1);
        Mockito.when(riskResultMapper.delete(any(QueryWrapper.class))).thenReturn(1);
        Mockito.when(riskResultMapper.insert(any(OrderRiskResult.class))).thenReturn(1);
        Mockito.when(standardizerMetaMapper.delete(any(QueryWrapper.class))).thenReturn(1);
        Mockito.when(standardizerMetaMapper.insert(any(OrderRiskStandardizerMeta.class))).thenReturn(1);
        Mockito.when(modelMetaMapper.insert(any(OrderRiskModelMeta.class))).thenReturn(1);
        Mockito.when(modelMetaMapper.update(eq(null), any(UpdateWrapper.class))).thenReturn(1);
        Mockito.when(feedbackMapper.insert(any(OrderRiskFeedback.class))).thenReturn(1);
    }

    @Test
    void shouldPersistSnapshotAndResultWhenScoringByRuleFallback() throws Exception {
        Long orderId = 1001L;
        Orders order = buildOrder(orderId, 2001L, Date.from(Instant.now().minus(2, ChronoUnit.HOURS)), "测试订单");
        Mockito.when(ordersService.selectById(orderId)).thenReturn(order);
        Mockito.when(modelMetaMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        Mockito.when(ordersService.countRiskHistory(any(), any(), any(), any())).thenReturn(0L);
        Mockito.when(ordersService.findRiskWindow(any(), any())).thenReturn(List.of(order));
        Mockito.when(orderDetailService.findByOrder(any())).thenReturn(List.of(buildOrderDetail(1L, 2)));

        service.scoreOrder(orderId, "submit");

        ArgumentCaptor<OrderRiskFeatureSnapshot> snapshotCaptor = ArgumentCaptor.forClass(OrderRiskFeatureSnapshot.class);
        Mockito.verify(featureSnapshotMapper).insert(snapshotCaptor.capture());
        Assertions.assertEquals(orderId, snapshotCaptor.getValue().getOrderId());
        Assertions.assertEquals("v1", snapshotCaptor.getValue().getFeatureVersion());

        ArgumentCaptor<OrderRiskResult> resultCaptor = ArgumentCaptor.forClass(OrderRiskResult.class);
        Mockito.verify(riskResultMapper).insert(resultCaptor.capture());
        OrderRiskResult inserted = resultCaptor.getValue();
        Assertions.assertEquals("rule-baseline-v1", inserted.getModelVersion());
        Map<String, Object> reason = objectMapper.readValue(inserted.getReasonJson(), new TypeReference<>() {
        });
        Assertions.assertEquals("rule", reason.get("mode"));
        List<?> ruleHits = (List<?>) reason.get("ruleHits");
        Assertions.assertFalse(ruleHits.isEmpty());
    }

    @Test
    void shouldUseModelPathWhenBundleIsAvailable() throws Exception {
        Long orderId = 1002L;
        Orders order = buildOrder(orderId, 2002L, Date.from(Instant.now().minus(1, ChronoUnit.HOURS)), "model-case");
        Mockito.when(ordersService.selectById(orderId)).thenReturn(order);
        Mockito.when(ordersService.countRiskHistory(any(), any(), any(), any())).thenReturn(0L);
        Mockito.when(ordersService.findRiskWindow(any(), any())).thenReturn(List.of(order));
        Mockito.when(orderDetailService.findByOrder(any())).thenReturn(List.of(buildOrderDetail(2L, 1)));

        LogisticRegression lr = Mockito.mock(LogisticRegression.class);
        Mockito.doAnswer(invocation -> {
            double[] posterior = invocation.getArgument(1);
            posterior[0] = 0.2D;
            posterior[1] = 0.8D;
            return 1;
        }).when(lr).predict(any(double[].class), any(double[].class));

        RandomForest rf = Mockito.mock(RandomForest.class);
        Mockito.doAnswer(invocation -> {
            double[] posterior = invocation.getArgument(1);
            posterior[0] = 0.7D;
            posterior[1] = 0.3D;
            return 1;
        }).when(rf).predict(any(), any(double[].class));
        Mockito.when(rf.importance()).thenReturn(new double[]{0.3, 0.1, 0.2, 0.05, 0.03, 0.02, 0.04, 0.06, 0.01});

        OrderRiskServiceImpl.ModelBundle bundle = new OrderRiskServiceImpl.ModelBundle();
        bundle.setModelVersion("model-test-v1");
        bundle.setFeatureVersion("v1");
        bundle.setLrModel(lr);
        bundle.setRfModel(rf);
        bundle.setStandardizerMeta(buildStandardizerMeta());

        setModelRefBundle(bundle);
        setLastModelRefreshTime(Instant.now());

        service.scoreOrder(orderId, "submit");

        ArgumentCaptor<OrderRiskResult> resultCaptor = ArgumentCaptor.forClass(OrderRiskResult.class);
        Mockito.verify(riskResultMapper).insert(resultCaptor.capture());
        OrderRiskResult inserted = resultCaptor.getValue();
        Map<String, Object> reason = objectMapper.readValue(inserted.getReasonJson(), new TypeReference<>() {
        });
        Assertions.assertEquals("model", reason.get("mode"));
        Assertions.assertEquals("model-test-v1", reason.get("modelVersion"));
        List<?> topFeatures = (List<?>) reason.get("topFeatures");
        Assertions.assertFalse(topFeatures.isEmpty());
        Assertions.assertTrue(inserted.getRiskScore() >= 0 && inserted.getRiskScore() <= 100);
    }

    @Test
    void shouldWriteFeedbackAndUpdateLatestDecisionStatus() {
        Long orderId = 1003L;
        OrderRiskResult latest = new OrderRiskResult();
        latest.setOrderId(orderId);
        latest.setModelVersion("order-risk-20260426203624");
        Mockito.when(riskResultMapper.selectOne(any(QueryWrapper.class))).thenReturn(latest);

        Mockito.when(ordersService.selectById(orderId)).thenReturn(buildOrder(orderId, 2001L, new Date(), ""));
        service.submitFeedback(orderId, "reject", 9001L, "manual-check");

        ArgumentCaptor<OrderRiskFeedback> feedbackCaptor = ArgumentCaptor.forClass(OrderRiskFeedback.class);
        Mockito.verify(feedbackMapper).insert(feedbackCaptor.capture());
        Assertions.assertEquals("reject", feedbackCaptor.getValue().getDecision());
        Assertions.assertEquals(orderId, feedbackCaptor.getValue().getOrderId());
        Mockito.verify(riskResultMapper).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void shouldInsertActiveModelMetaWhenReleaseGatePasses() {
        prepareTrainingDataset();
        Mockito.when(modelMetaMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        service.runScheduledTraining();

        ArgumentCaptor<OrderRiskModelMeta> modelCaptor = ArgumentCaptor.forClass(OrderRiskModelMeta.class);
        Mockito.verify(modelMetaMapper).insert(modelCaptor.capture());
        Assertions.assertEquals("ACTIVE", modelCaptor.getValue().getStatus());
    }

    @Test
    void shouldInsertRejectedModelMetaWhenMetricsRegressBeyondThreshold() throws Exception {
        prepareTrainingDataset();
        properties.getTraining().getRejectThresholds().setPrAucDelta(0D);
        properties.getTraining().getRejectThresholds().setRecallDelta(0D);

        OrderRiskModelMeta active = new OrderRiskModelMeta();
        active.setModelVersion("active-v1");
        active.setStatus("ACTIVE");
        active.setArtifactPath(tempDir.resolve("missing-model.ser").toString());
        active.setMetricsJson(objectMapper.writeValueAsString(Map.of(
                "prAuc", 1.0,
                "recallTop20", 1.0
        )));
        Mockito.when(modelMetaMapper.selectOne(any(QueryWrapper.class))).thenReturn(active, active);

        service.runScheduledTraining();

        ArgumentCaptor<OrderRiskModelMeta> modelCaptor = ArgumentCaptor.forClass(OrderRiskModelMeta.class);
        Mockito.verify(modelMetaMapper).insert(modelCaptor.capture());
        Assertions.assertEquals("REJECTED", modelCaptor.getValue().getStatus());
    }

    @Test
    void missingOrderDoesNotWriteAndFeedbackRefusesIt() {
        service.scoreOrder(999L, "test");
        Mockito.verify(riskResultMapper, Mockito.never()).insert(any(OrderRiskResult.class));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.submitFeedback(999L, "reject", 1L, ""));
        Mockito.verify(feedbackMapper, Mockito.never()).insert(any(OrderRiskFeedback.class));
    }

    @Test
    void noOrdersAndDependencyFailureAreDifferentFromTrainingSuccess() {
        Mockito.when(ordersService.findRiskWindow(any(),any())).thenReturn(List.of());
        var skipped = service.runTraining();
        Assertions.assertEquals("SKIPPED", skipped.get("status"));
        Assertions.assertEquals("no_orders_in_window", skipped.get("reason"));
        Assertions.assertEquals(false, skipped.get("trained"));
        Mockito.when(ordersService.findRiskWindow(any(),any())).thenThrow(new IllegalStateException("private fixture dependency"));
        var failed = service.runTraining();
        Assertions.assertEquals("FAILED", failed.get("status"));
        Assertions.assertEquals("execution_failure", failed.get("reason"));
        Assertions.assertFalse(failed.toString().contains("private fixture"));
        Mockito.verify(modelMetaMapper, Mockito.never()).insert(any(OrderRiskModelMeta.class));
    }

    @Test
    void missingFeaturesAreExplicitRuleFallbackAndExcludedFromTraining() throws Exception {
        Orders order = buildOrder(800L, 2001L, new Date(), "");
        order.setAmount(null);
        Mockito.when(ordersService.selectById(800L)).thenReturn(order);
        Mockito.when(orderDetailService.findByOrder(800L)).thenReturn(List.of());
        service.scoreOrder(800L, "test");
        var result = ArgumentCaptor.forClass(OrderRiskResult.class);
        Mockito.verify(riskResultMapper).insert(result.capture());
        Map<?, ?> reason = objectMapper.readValue(result.getValue().getReasonJson(), Map.class);
        Assertions.assertEquals("INCOMPLETE", reason.get("dataStatus"));
        Assertions.assertEquals("rule", reason.get("mode"));
        Assertions.assertEquals("UNLABELLED_CANDIDATE", reason.get("labelStatus"));
    }

    @Test
    void missingModelAndEmptyMetricsHaveDistinctReplayStatus() throws Exception {
        Assertions.assertEquals("MODEL_MISSING", service.getReplaySummary("missing").get("replayStatus"));
        OrderRiskModelMeta meta = new OrderRiskModelMeta();
        meta.setModelVersion("empty"); meta.setArtifactPath(tempDir.resolve("empty.ser").toString());
        java.nio.file.Files.writeString(Path.of(meta.getArtifactPath()), "fixture");
        Mockito.when(modelMetaMapper.selectOne(any(QueryWrapper.class))).thenReturn(meta);
        Assertions.assertEquals("NO_METRICS", service.getReplaySummary("empty").get("replayStatus"));
    }

    @Test
    void trainingFailureLogsCauseTypesAndLocationsWithoutDependencyMessages() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OrderRiskServiceImpl.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            Mockito.when(ordersService.findRiskWindow(any(),any())).thenThrow(
                    new IllegalStateException("token=private-fixture", new IllegalArgumentException("private SQL value")));
            Assertions.assertEquals("FAILED", service.runTraining().get("status"));
            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining());
            Assertions.assertTrue(logs.contains("IllegalStateException") && logs.contains("IllegalArgumentException"));
            Assertions.assertFalse(logs.contains("private-fixture") || logs.contains("private SQL"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private void prepareTrainingDataset() {
        Instant now = Instant.now();
        List<Orders> orders = new ArrayList<>();
        List<OrderRiskFeatureSnapshot> snapshots = new ArrayList<>();
        List<OrderRiskFeedback> feedbacks = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            long orderId = i + 1L;
            Instant ts = i < 5 ? now.minus(50 - i, ChronoUnit.DAYS) : now.minus(10 - i, ChronoUnit.DAYS);
            Orders order = buildOrder(orderId, 3000L + i, Date.from(ts), "seed-" + i);
            order.setStatus(i == 0 || i == 6 ? Orders.CANCELLED : Orders.COMPLETED);
            orders.add(order);

            OrderRiskFeatureSnapshot snapshot = new OrderRiskFeatureSnapshot();
            snapshot.setOrderId(orderId);
            snapshot.setFeatureVersion("v1");
            snapshot.setCreatedAt(new Date());
            snapshot.setSnapshotJson(buildSnapshotJson(i));
            snapshots.add(snapshot);

            OrderRiskFeedback feedback = new OrderRiskFeedback();
            feedback.setOrderId(orderId);
            feedback.setCreatedAt(new Date());
            feedback.setDecision((i == 0 || i == 6) ? "reject" : "approve");
            feedbacks.add(feedback);
        }

        Mockito.when(ordersService.findRiskWindow(any(), any())).thenReturn(orders);
        Mockito.when(featureSnapshotMapper.selectList(any(QueryWrapper.class))).thenReturn(snapshots);
        Mockito.when(feedbackMapper.selectList(any(QueryWrapper.class))).thenReturn(feedbacks);
    }

    private Orders buildOrder(Long id, Long userId, Date orderTime, String remark) {
        Orders order = new Orders();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderTime(orderTime);
        order.setAmount(BigDecimal.valueOf(39.9));
        order.setRemark(remark);
        order.setStatus(Orders.COMPLETED);
        order.setPayStatus(Orders.PAID);
        return order;
    }

    private OrderDetail buildOrderDetail(Long dishId, int number) {
        OrderDetail detail = new OrderDetail();
        detail.setDishId(dishId);
        detail.setNumber(number);
        return detail;
    }

    private Map<String, double[]> buildStandardizerMeta() {
        Map<String, double[]> result = new LinkedHashMap<>();
        result.put("order_amount", new double[]{0D, 1D});
        result.put("item_count", new double[]{0D, 1D});
        result.put("unique_dish_count", new double[]{0D, 1D});
        result.put("remark_length", new double[]{0D, 1D});
        result.put("order_count_1h", new double[]{0D, 1D});
        result.put("order_count_24h", new double[]{0D, 1D});
        result.put("cancel_rate_30d", new double[]{0D, 1D});
        result.put("avg_amount_30d", new double[]{0D, 1D});
        result.put("order_amount_to_avg_ratio_30d", new double[]{0D, 1D});
        return result;
    }

    private String buildSnapshotJson(int seed) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("order_amount", 20D + seed);
        map.put("item_count", 1 + (seed % 3));
        map.put("unique_dish_count", 1 + (seed % 2));
        map.put("remark_length", 2 + seed);
        map.put("order_count_1h", seed % 4);
        map.put("order_count_24h", 2 + seed);
        map.put("cancel_rate_30d", 0.1D * (seed % 3));
        map.put("avg_amount_30d", 18D + seed);
        map.put("order_amount_to_avg_ratio_30d", 1D + 0.1D * seed);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void setModelRefBundle(OrderRiskServiceImpl.ModelBundle bundle) throws Exception {
        Field modelRefField = OrderRiskServiceImpl.class.getDeclaredField("modelRef");
        modelRefField.setAccessible(true);
        AtomicReference<OrderRiskServiceImpl.ModelBundle> modelRef =
                (AtomicReference<OrderRiskServiceImpl.ModelBundle>) modelRefField.get(service);
        modelRef.set(bundle);
    }

    private void setLastModelRefreshTime(Instant instant) throws Exception {
        Field refreshField = OrderRiskServiceImpl.class.getDeclaredField("lastModelRefreshTime");
        refreshField.setAccessible(true);
        refreshField.set(service, instant);
    }
}

