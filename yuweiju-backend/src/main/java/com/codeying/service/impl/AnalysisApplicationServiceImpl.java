package com.codeying.service.impl;

import com.codeying.constant.RedisKeys;
import com.codeying.entity.AddressBook;
import com.codeying.entity.Dish;
import com.codeying.entity.DishFlavor;
import com.codeying.entity.DishHeatPredictionResult;
import com.codeying.entity.DishTimeseriesFeatureSnapshot;
import com.codeying.entity.OrderDetail;
import com.codeying.entity.Orders;
import com.codeying.entity.User;
import com.codeying.entity.UserBehaviorFeatureSnapshot;
import com.codeying.entity.UserClusterResult;
import com.codeying.mapper.DishHeatPredictionResultMapper;
import com.codeying.mapper.DishTimeseriesFeatureSnapshotMapper;
import com.codeying.mapper.UserBehaviorFeatureSnapshotMapper;
import com.codeying.mapper.UserClusterResultMapper;
import com.codeying.properties.AnalysisProperties;
import com.codeying.mapper.AddressBookMapper;
import com.codeying.service.AiWeatherService;
import com.codeying.service.AnalysisApplicationService;
import com.codeying.vo.admin.analysis.AnalysisRun;
import com.codeying.vo.admin.analysis.AnalysisStage;
import com.codeying.analysis.HeatModelStore;
import com.codeying.exception.BusinessException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.codeying.service.AnalysisObservationService;
import com.codeying.service.DishFlavorService;
import com.codeying.service.DishService;
import com.codeying.mapper.OrderDetailMapper;
import com.codeying.mapper.OrdersMapper;
import com.codeying.service.UserService;
import com.codeying.vo.admin.analysis.DishHeatPredictionVO;
import com.codeying.vo.admin.analysis.UserClusterResultVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 热度分析与数据挖掘应用服务实现。
 *
 * @author Endercloud
 */
@Slf4j
@Service
public class AnalysisApplicationServiceImpl implements AnalysisApplicationService {
/**
 * ZoneId.of
 * @return 
 */

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
/**
 * Pattern.compile
 * @return 
 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]+");
    private static final int DEFAULT_LIMIT = 20;
    private static final String DEFAULT_WEATHER_CODE = "CLEAR";
    private static final float MISSING_VALUE = Float.NaN;
    private static final int HEAT_FEATURE_COUNT = 21;

    private final com.codeying.analysis.HeatFeatures heatFeatures;
    private final com.codeying.mapper.AnalysisFeatureMapper featureMapper;
    private final TransactionTemplate transactions;
    private final HeatModelStore modelStore;
    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final DishService dishService;
    private final DishFlavorService dishFlavorService;
    private final UserService userService;
    private final AddressBookMapper addressBookMapper;
    private final UserBehaviorFeatureSnapshotMapper userBehaviorFeatureSnapshotMapper;
    private final DishTimeseriesFeatureSnapshotMapper dishTimeseriesFeatureSnapshotMapper;
    private final DishHeatPredictionResultMapper dishHeatPredictionResultMapper;
    private final UserClusterResultMapper userClusterResultMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AnalysisProperties analysisProperties;
    private final AiWeatherService aiWeatherService;
    private final AnalysisObservationService analysisObservationService;

    public AnalysisApplicationServiceImpl(
            com.codeying.analysis.HeatFeatures heatFeatures,
            com.codeying.mapper.AnalysisFeatureMapper featureMapper,
            PlatformTransactionManager transactionManager,
            HeatModelStore modelStore,
            OrdersMapper ordersMapper,
            OrderDetailMapper orderDetailMapper,
            DishService dishService,
            DishFlavorService dishFlavorService,
            UserService userService,
            AddressBookMapper addressBookMapper,
            UserBehaviorFeatureSnapshotMapper userBehaviorFeatureSnapshotMapper,
            DishTimeseriesFeatureSnapshotMapper dishTimeseriesFeatureSnapshotMapper,
            DishHeatPredictionResultMapper dishHeatPredictionResultMapper,
            UserClusterResultMapper userClusterResultMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AnalysisProperties analysisProperties,
            AiWeatherService aiWeatherService,
            AnalysisObservationService analysisObservationService
    ) {
        this.heatFeatures = heatFeatures;
        this.featureMapper = featureMapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.modelStore = modelStore;
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.dishService = dishService;
        this.dishFlavorService = dishFlavorService;
        this.userService = userService;
        this.addressBookMapper = addressBookMapper;
        this.userBehaviorFeatureSnapshotMapper = userBehaviorFeatureSnapshotMapper;
        this.dishTimeseriesFeatureSnapshotMapper = dishTimeseriesFeatureSnapshotMapper;
        this.dishHeatPredictionResultMapper = dishHeatPredictionResultMapper;
        this.userClusterResultMapper = userClusterResultMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.analysisProperties = analysisProperties;
        this.aiWeatherService = aiWeatherService;
        this.analysisObservationService = analysisObservationService;
    }

    @Override
    public void generateDailySnapshots(LocalDate snapshotDate) {
        LocalDate targetDate = normalizeDate(snapshotDate);
        log.info("Generate daily snapshots start, snapshotDate={}, featureVersion={}", targetDate, analysisProperties.getFeatureVersion());

        List<User> users = userService.list();
        List<Dish> dishes = dishService.list();
        Map<Long, Dish> dishMap = dishes.stream()
                .filter(dish -> dish != null && dish.getId() != null)
                .collect(Collectors.toMap(Dish::getId, dish -> dish, (a, b) -> a));
        Map<Long, List<DishFlavor>> flavorMap = dishFlavorService.list().stream()
                .filter(flavor -> flavor != null && flavor.getDishId() != null)
                .collect(Collectors.groupingBy(DishFlavor::getDishId));

        AnalysisWindowData window30 = loadWindowData(targetDate, 30);
        List<DishTimeseriesFeatureSnapshot> dishSnapshots = buildDishSnapshots(targetDate, dishes, window30);
        transactions.executeWithoutResult(tx -> {
            saveUserSnapshots(targetDate, users, flavorMap, window30);
            saveDishSnapshots(targetDate, dishSnapshots);
        });

        log.info("Generate daily snapshots done, snapshotDate={}, userCount={}, dishCount={}", targetDate, users.size(), dishes.size());
    }

    @Override
    public WeeklyTrainingResult runWeeklyTraining(LocalDate date) {
        AnalysisRun run = runTrainingUseCase(date);
        Map<String, Object> data = run.response();
        return new WeeklyTrainingResult(normalizeDate(date), run.executed(),
                ((Number) data.getOrDefault("userSnapshotCount", 0)).intValue(),
                ((Number) data.getOrDefault("clusterCount", 0)).intValue(),
                (String) data.get("clusterModelVersion"), Boolean.TRUE.equals(data.get("heatModelTrained")),
                run.executed() ? null : (String) data.get("skipReason"));
    }

    @Override
    public AnalysisRun runSnapshotUseCase(LocalDate date) {
        LocalDate target = normalizeDate(date);
        AnalysisRun run = new AnalysisRun(target);
        try {
            generateDailySnapshots(target);
            long users = userBehaviorFeatureSnapshotMapper.countDaily(target, analysisProperties.getFeatureVersion());
            long dishes = dishTimeseriesFeatureSnapshotMapper.countDaily(target, analysisProperties.getFeatureVersion());
            run.add(stage("snapshot", users > 0 && dishes > 0 ? "COMPLETE" : "PARTIAL",
                    users == 0 ? "NO_USER_SNAPSHOTS" : dishes == 0 ? "NO_DISH_SNAPSHOTS" : null, target.minusDays(30), target.minusDays(1),
                    Map.of("userSnapshotCount", users, "dishSnapshotCount", dishes, "featureVersion", analysisProperties.getFeatureVersion())));
        } catch (RuntimeException ex) { run.add(failure("snapshot", target, ex)); }
        return run;
    }

    @Override
    public AnalysisRun runManualTraining(LocalDate date) {
        LocalDate target = normalizeDate(date);
        AnalysisRun run = runSnapshotUseCase(target);
        if (run.stages().stream().anyMatch(s -> "FAILED".equals(s.status()))) {
            run.executed(false);
            run.add(stage("training", "SKIPPED", "SNAPSHOT_FAILED", target, target, Map.of()));
            return run;
        }
        AnalysisRun training = runTrainingUseCase(target);
        training.stages().forEach(run::add);
        training.response().forEach((key, value) -> { if (!List.of("stages", "status", "message").contains(key)) run.put(key, value); });
        run.executed(training.executed());
        if (!training.executed()) {
            run.add(stage("prediction", "SKIPPED", "TRAINING_NOT_EXECUTED", target, target, Map.of()));
            return run;
        }
        runPredictionUseCase(target).stages().forEach(run::add);
        return run;
    }

    @Override
    public AnalysisRun runTrainingUseCase(LocalDate date) {
        LocalDate target = normalizeDate(date);
        AnalysisRun run = new AnalysisRun(target);
        run.put("heatModelTrained", false);
        List<UserBehaviorFeatureSnapshot> snapshots;
        try { snapshots = userBehaviorFeatureSnapshotMapper.findDaily(target, analysisProperties.getFeatureVersion()); }
        catch (RuntimeException ex) { run.executed(false); run.add(failure("clustering", target, ex)); return run; }
        run.put("userSnapshotCount", snapshots.size());
        if (snapshots.isEmpty()) {
            run.executed(false);
            run.put("skipReason", "NO_USER_SNAPSHOTS");
            run.put("clusterCount", 0);
            run.put("clusterModelVersion", null);
            run.add(stage("clustering", "SKIPPED", "NO_USER_SNAPSHOTS", target, target, Map.of("userSnapshotCount", 0)));
            return run;
        }
        try {
            var clusters = com.codeying.analysis.UserClustering.cluster(snapshots, analysisProperties.getClusterCount());
            Map<Integer, List<Long>> topDishes = buildClusterTopDishMap(target, snapshots, clusters.assignments());
            String version = buildModelVersion("cluster", target);
            transactions.executeWithoutResult(tx -> {
                userClusterResultMapper.deleteDaily(target);
                for (int i = 0; i < snapshots.size(); i++) {
                    UserClusterResult result = new UserClusterResult();
                    result.setSnapshotDate(target); result.setUserId(snapshots.get(i).getUserId());
                    result.setClusterId(clusters.assignments().get(i)); result.setClusterScore(scale(clusters.confidences().get(i), 6));
                    result.setModelVersion(version); result.setFeatureVersion(analysisProperties.getFeatureVersion());
                    result.setTopnDishJson(writeJson(topDishes.getOrDefault(result.getClusterId(), List.of())));
                    userClusterResultMapper.insert(result);
                }
            });
            run.put("clusterCount", topDishes.size()); run.put("clusterModelVersion", version);
            run.add(stage("clustering", "COMPLETE", null, target.minusDays(30), target.minusDays(1),
                    Map.of("userSnapshotCount", snapshots.size(), "clusterRowCount", userClusterResultMapper.countDaily(target),
                            "clusterCount", topDishes.size(), "modelVersion", version, "silhouette", clusters.silhouette(),
                            "silhouetteThreshold", analysisProperties.getValidation().getSilhouetteMin(),
                            "silhouettePassed", clusters.silhouette() >= analysisProperties.getValidation().getSilhouetteMin())));
            try {
                for (int i = 0; i < snapshots.size(); i++) {
                    int cluster = clusters.assignments().get(i);
                    writeCache(RedisKeys.userClusterKey(snapshots.get(i).getUserId()),
                            writeJson(new ClusterCachePayload(cluster, topDishes.getOrDefault(cluster, List.of()))),
                            analysisProperties.getRedis().getClusterUserTtlHours());
                }
                for (var entry : topDishes.entrySet()) writeCache(RedisKeys.clusterTopnKey(entry.getKey()),
                        writeJson(entry.getValue()), analysisProperties.getRedis().getClusterTopnTtlHours());
                run.add(stage("clusterCache", "COMPLETE", null, target, target, Map.of("cachedUserCount", snapshots.size(), "clusterKeyCount", topDishes.size())));
            } catch (RuntimeException ex) { run.add(failure("clusterCache", target, ex)); }
        } catch (RuntimeException ex) {
            run.executed(false); run.add(failure("clustering", target, ex)); return run;
        }
        LocalDate start = target.minusDays(analysisProperties.getTrainingLookbackDays());
        LocalDate end = target.minusDays(analysisProperties.getPredictionWindowDays());
        if (!Boolean.TRUE.equals(analysisProperties.getXgboost().getEnabled())) {
            run.add(stage("heatTraining", "SKIPPED", "XGBOOST_DISABLED", start, end, Map.of()));
            return run;
        }
        try {
            List<DishTimeseriesFeatureSnapshot> dishes = dishTimeseriesFeatureSnapshotMapper.findDaily(target, analysisProperties.getFeatureVersion());
            if (dishes.isEmpty()) {
                run.add(stage("heatTraining", "SKIPPED", "NO_DISH_SNAPSHOTS", start, end, Map.of()));
                return run;
            }
            Booster booster = trainHeatModel(target);
            if (booster == null) {
                run.add(stage("heatTraining", "SKIPPED", "INSUFFICIENT_SAMPLES", start, end, Map.of("trainSampleCount", 0)));
                run.add(stage("validation", "SKIPPED", "NO_NEW_MODEL", start, end, Map.of()));
                return run;
            }
            booster.dispose();
            Map<String, Object> meta = modelStore.metadata();
            run.put("heatModelTrained", true);
            run.add(stage("heatTraining", "COMPLETE", null, start, end, meta));
            try {
                Map<String, Object> metrics = logHeatValidation(target);
                run.add(stage("validation", metrics.isEmpty() ? "SKIPPED" : "COMPLETE", metrics.isEmpty() ? "NO_EVAL_SAMPLES" : null, start, end, metrics));
            } catch (RuntimeException ex) { run.add(failure("validation", target, ex)); }
        } catch (RuntimeException ex) { run.add(failure("heatTraining", target, ex)); }
        return run;
    }

    private AnalysisStage stage(String name, String status, String reason, LocalDate start, LocalDate end, Map<String, Object> outputs) {
        return AnalysisStage.of(name, status, reason, start, end, outputs);
    }

    private AnalysisStage failure(String name, LocalDate date, RuntimeException ex) {
        // Preserve the full cause in the failure event without serializing/logging unsafe messages.
        BusinessException cause = ex instanceof BusinessException business ? business : new BusinessException("分析依赖或持久化失败", ex);
        log.warn("Analysis stage failed, stage={}, date={}, causeType={}", name, date, cause.getCause() == null
                ? cause.getClass().getSimpleName() : cause.getCause().getClass().getSimpleName());
        return new AnalysisStage(name, "FAILED", "DEPENDENCY_FAILURE", date, date, Map.of("message", "分析阶段失败，请检查依赖和保留的产物"), cause);
    }

    @Override
    public void runDailyPrediction(LocalDate windowStart) { runPredictionUseCase(windowStart); }

    @Override
    public AnalysisRun runPredictionUseCase(LocalDate windowStart) {
        LocalDate target = normalizeDate(windowStart);
        AnalysisRun run = new AnalysisRun(target);
        run.put("windowStart", target);
        try { predictDaily(target, run); }
        catch (RuntimeException ex) { run.add(failure("prediction", target, ex)); }
        return run;
    }

    private void predictDaily(LocalDate windowStart, AnalysisRun run) {
        LocalDate targetDate = normalizeDate(windowStart);
        LocalDate windowEnd = targetDate.plusDays(Math.max(1, analysisProperties.getPredictionWindowDays()) - 1L);
        List<DishTimeseriesFeatureSnapshot> snapshots = dishTimeseriesFeatureSnapshotMapper.findDaily(targetDate, analysisProperties.getFeatureVersion());
        if (snapshots == null || snapshots.isEmpty()) {
            run.add(stage("prediction", "SKIPPED", "NO_DISH_SNAPSHOTS", targetDate, windowEnd, Map.of("predictionCount", 0)));
            return;
        }

        Map<Long, Dish> dishMap = dishService.list().stream()
                .filter(dish -> dish != null && dish.getId() != null)
                .collect(Collectors.toMap(Dish::getId, dish -> dish, (a, b) -> a));
        HeatModelStore.Loaded loaded = modelStore.load();
        Booster heatBooster = loaded == null ? null : loaded.booster();
        try {
        Map<Long, Double> categoryMeanMap = buildCategoryMeanMap(snapshots, targetDate, heatBooster);
        double globalMean = categoryMeanMap.values().stream().mapToDouble(Double::doubleValue).average().orElse(1D);
        int smoothingM = Math.max(1, analysisProperties.getColdStart().getBayesianSmoothingM());
        String modelVersion = loaded == null ? "baseline-" + analysisProperties.getFeatureVersion() : loaded.version();

        List<DishHeatPredictionResult> results = new ArrayList<>(snapshots.size());
        for (DishTimeseriesFeatureSnapshot snapshot : snapshots) {
            double predictedSalesQty = predictWindowSales(snapshot, targetDate, heatBooster);
            if ((snapshot.getSalesQty30d() == null || snapshot.getSalesQty30d() == 0) && snapshot.getCategoryId() != null) {
                double categoryMean = categoryMeanMap.getOrDefault(snapshot.getCategoryId(), globalMean);
                double weight = snapshot.getSalesQty30d() == null ? 0D : snapshot.getSalesQty30d();
                predictedSalesQty = ((weight * predictedSalesQty) + (smoothingM * categoryMean)) / (weight + smoothingM);
            }
            double heatScore = Math.max(
                    predictedSalesQty + percentage(snapshot.getSalesQty7d(), 7D) * 0.15D + decimal(snapshot.getDecaySales30d()) * 0.10D,
                    0D
            );

            Map<String, Object> explain = new LinkedHashMap<>();
            explain.put("xgboostEnabled", Boolean.TRUE.equals(analysisProperties.getXgboost().getEnabled()));
            explain.put("predictedWindowSales", scale(predictedSalesQty, 4));
            explain.put("salesQty7d", valueOrZero(snapshot.getSalesQty7d()));
            explain.put("salesQty30d", valueOrZero(snapshot.getSalesQty30d()));
            explain.put("decaySales30d", scale(decimal(snapshot.getDecaySales30d()), 4));
            explain.put("isPromo", valueOrZero(snapshot.getIsPromo()));
            explain.put("weatherCode", snapshot.getWeatherCode());
            explain.put("longTail", heatFeatures.isLongTailSnapshot(snapshot));
            explain.put("categoryId", snapshot.getCategoryId());
            Dish dish = dishMap.get(snapshot.getDishId());
            explain.put("dishName", dish == null ? null : dish.getName());

            DishHeatPredictionResult result = new DishHeatPredictionResult();
            result.setWindowStart(targetDate);
            result.setWindowEnd(windowEnd);
            result.setDishId(snapshot.getDishId());
            result.setPredictedSalesQty(scale(predictedSalesQty, 4));
            result.setHeatScore(scale(heatScore, 6));
            result.setModelVersion(modelVersion);
            result.setFeatureVersion(analysisProperties.getFeatureVersion());
            result.setExplainJson(writeJson(explain));
            result.setStatus(1);
            results.add(result);
        }
        transactions.executeWithoutResult(tx -> {
            dishHeatPredictionResultMapper.deleteWindow(targetDate, windowEnd);
            for (DishHeatPredictionResult result : results) dishHeatPredictionResultMapper.insert(result);
        });
        run.add(stage("prediction", loaded == null ? "PARTIAL" : "COMPLETE", loaded == null ? "NO_MODEL_BASELINE" : null,
                targetDate, windowEnd, Map.of("predictionCount", dishHeatPredictionResultMapper.countWindow(targetDate, windowEnd),
                        "dishSnapshotCount", snapshots.size(), "modelVersion", modelVersion, "modelLoaded", loaded != null)));
        try {
        for (DishHeatPredictionResult result : results) {
            writeCache(
                    RedisKeys.dishHeatPredictionKey(targetDate.toString(), result.getDishId()),
                    writeJson(result),
                    analysisProperties.getRedis().getHeatPredictionTtlHours()
            );
        }

        List<Long> fallbackDishIds = results.stream()
                .sorted(Comparator.comparing(DishHeatPredictionResult::getHeatScore, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .map(DishHeatPredictionResult::getDishId)
                .filter(Objects::nonNull)
                .limit(Math.max(1, analysisProperties.getColdStart().getTopN()))
                .toList();
        writeCache(
                RedisKeys.heatFallbackTopKey(analysisProperties.getColdStart().getTopN()),
                writeJson(fallbackDishIds),
                analysisProperties.getRedis().getFallbackTtlHours()
        );

        run.add(stage("predictionCache", "COMPLETE", null, targetDate, windowEnd, Map.of("predictionCount", results.size())));
        } catch (RuntimeException ex) { run.add(failure("predictionCache", targetDate, ex)); }
        } finally { if (loaded != null) loaded.close(); }
    }

    @Override
    public void backfillSnapshots(LocalDate endDate, Integer days) {
        LocalDate targetEndDate = normalizeDate(endDate);
        int totalDays = days == null ? analysisProperties.getBackfillDays() : days;
        if (totalDays < 1 || totalDays > 365) throw new BusinessException("回填天数必须在 1 到 365 之间");
        log.info("Backfill snapshots start, endDate={}, days={}", targetEndDate, totalDays);
        for (int i = 0; i < totalDays; i++) {
            LocalDate snapshotDate = targetEndDate.minusDays(i);
            generateDailySnapshots(snapshotDate);
        }
        log.info("Backfill snapshots done, endDate={}, days={}", targetEndDate, totalDays);
    }

    @Override
    public List<DishHeatPredictionVO> listHeatPredictions(LocalDate windowStart, Integer limit) {
        LocalDate targetDate = normalizeDate(windowStart);
        int size = normalizeLimit(limit);
        List<DishHeatPredictionResult> results = dishHeatPredictionResultMapper.listActiveByHeat(targetDate, size);
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Dish> dishMap = dishService.listByIds(results.stream().map(DishHeatPredictionResult::getDishId).toList()).stream()
                .filter(dish -> dish != null && dish.getId() != null)
                .collect(Collectors.toMap(Dish::getId, dish -> dish, (a, b) -> a));
        List<DishHeatPredictionVO> voList = new ArrayList<>(results.size());
        for (DishHeatPredictionResult result : results) {
            DishHeatPredictionVO vo = new DishHeatPredictionVO();
            vo.setDishId(result.getDishId());
            vo.setDishName(dishMap.containsKey(result.getDishId()) ? dishMap.get(result.getDishId()).getName() : null);
            vo.setWindowStart(result.getWindowStart());
            vo.setWindowEnd(result.getWindowEnd());
            vo.setPredictedSalesQty(result.getPredictedSalesQty());
            vo.setHeatScore(result.getHeatScore());
            vo.setModelVersion(result.getModelVersion());
            vo.setFeatureVersion(result.getFeatureVersion());
            vo.setExplainJson(result.getExplainJson());
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public List<UserClusterResultVO> listUserClusters(LocalDate snapshotDate, Integer limit) {
        LocalDate targetDate = normalizeDate(snapshotDate);
        int size = normalizeLimit(limit);
        List<UserClusterResult> results = userClusterResultMapper.listDaily(targetDate, size);
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, User> userMap = userService.listByIds(results.stream().map(UserClusterResult::getUserId).toList()).stream()
                .filter(user -> user != null && user.getId() != null)
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        List<UserClusterResultVO> voList = new ArrayList<>(results.size());
        for (UserClusterResult result : results) {
            UserClusterResultVO vo = new UserClusterResultVO();
            vo.setUserId(result.getUserId());
            vo.setUserName(userMap.containsKey(result.getUserId()) ? userMap.get(result.getUserId()).getName() : null);
            vo.setSnapshotDate(result.getSnapshotDate());
            vo.setClusterId(result.getClusterId());
            vo.setClusterScore(result.getClusterScore());
            vo.setModelVersion(result.getModelVersion());
            vo.setFeatureVersion(result.getFeatureVersion());
            vo.setTopDishIds(readLongList(result.getTopnDishJson()));
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public List<Long> listRecommendedDishIdsForUser(Long userId, Integer limit) {
        int size = normalizeLimit(limit);
        if (userId != null) {
            ClusterCachePayload payload = readClusterPayload(userId);
            if (payload != null && payload.topDishIds() != null && !payload.topDishIds().isEmpty()) {
                return payload.topDishIds().stream().limit(size).toList();
            }

            List<Long> districtHotDishIds = listDistrictHotDishIds(userId, size);
            if (!districtHotDishIds.isEmpty()) {
                return districtHotDishIds;
            }
        }

        String cached = stringRedisTemplate.opsForValue().get(RedisKeys.heatFallbackTopKey(analysisProperties.getColdStart().getTopN()));
        if (StringUtils.hasText(cached)) {
            List<Long> cachedIds = readLongList(cached);
            if (!cachedIds.isEmpty()) {
                return cachedIds.stream().limit(size).toList();
            }
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        List<DishHeatPredictionResult> heatResults = dishHeatPredictionResultMapper.listActiveByHeat(today, size);
        if (heatResults != null && !heatResults.isEmpty()) {
            return heatResults.stream().map(DishHeatPredictionResult::getDishId).filter(Objects::nonNull).toList();
        }

        return orderDetailMapper.aggregateAnalysisSales(toDate(today.minusDays(30)), toDate(today), null, null, size)
                .stream().map(com.codeying.vo.admin.analysis.AnalysisDishSales::getDishId).toList();
    }

    @Override
    public Map<String, Object> getOfflineValidationMetrics(LocalDate snapshotDate) {
        LocalDate targetDate = normalizeDate(snapshotDate);
        String key = RedisKeys.analysisOfflineValidationKey(targetDate.toString());
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Read offline validation metrics failed, snapshotDate={}, causeType={}", targetDate, ex.getClass().getSimpleName());
            return Collections.emptyMap();
        }
    }

    private void saveUserSnapshots(
            LocalDate snapshotDate,
            List<User> users,
            Map<Long, List<DishFlavor>> flavorMap,
            AnalysisWindowData window30
    ) {
        String featureVersion = analysisProperties.getFeatureVersion();
        userBehaviorFeatureSnapshotMapper.deleteDaily(snapshotDate, featureVersion);

        Map<Long, UserFeatureAggregation> userAggregationMap = new HashMap<>();
        for (OrderContext context : window30.contexts()) {
            Long userId = context.order().getUserId();
            if (userId == null) {
                continue;
            }
            UserFeatureAggregation aggregation = userAggregationMap.computeIfAbsent(userId, ignored -> new UserFeatureAggregation());
            aggregation.consume(context, snapshotDate, flavorMap.getOrDefault(context.detail().getDishId(), Collections.emptyList()));
        }

        for (UserBehaviorFeatureSnapshot snapshot : featureMapper.buildUserSnapshots(snapshotDate, toDate(snapshotDate.minusDays(30)),
                toDate(snapshotDate), analysisProperties.getColdStart().getPriceBand().getLowMax(),
                analysisProperties.getColdStart().getPriceBand().getMidMax())) {
            UserFeatureAggregation aggregation = userAggregationMap.getOrDefault(snapshot.getUserId(), new UserFeatureAggregation());
            snapshot.setRfmMonetary30d(scale(snapshot.getRfmMonetary30d(), 2));
            snapshot.setOrderLunchRatio(scale(snapshot.getOrderLunchRatio(), 4));
            snapshot.setOrderDinnerRatio(scale(snapshot.getOrderDinnerRatio(), 4));
            snapshot.setOrderNightRatio(scale(snapshot.getOrderNightRatio(), 4));
            snapshot.setPriceLowRatio(scale(snapshot.getPriceLowRatio(), 4));
            snapshot.setPriceMidRatio(scale(snapshot.getPriceMidRatio(), 4));
            snapshot.setPriceHighRatio(scale(snapshot.getPriceHighRatio(), 4));
            snapshot.setFlavorVectorJson(writeJson(aggregation.flavorVector()));
            snapshot.setFeatureVersion(featureVersion);
            userBehaviorFeatureSnapshotMapper.insert(snapshot);
        }
    }

    private void saveDishSnapshots(LocalDate snapshotDate, List<DishTimeseriesFeatureSnapshot> snapshots) {
        String featureVersion = analysisProperties.getFeatureVersion();
        dishTimeseriesFeatureSnapshotMapper.deleteDaily(snapshotDate, featureVersion);
        for (DishTimeseriesFeatureSnapshot snapshot : snapshots) {
            dishTimeseriesFeatureSnapshotMapper.insert(snapshot);
        }
    }

    private List<DishTimeseriesFeatureSnapshot> buildDishSnapshots(
            LocalDate snapshotDate,
            List<Dish> dishes,
            AnalysisWindowData window30
    ) {
        String weatherCode = resolveWeatherCode(snapshotDate);
        List<DishTimeseriesFeatureSnapshot> snapshots = new ArrayList<>(dishes.size());
        Map<Long, Dish> dishMap = dishes.stream().collect(Collectors.toMap(Dish::getId, dish -> dish));
        for (DishTimeseriesFeatureSnapshot snapshot : featureMapper.buildDishSnapshots(toDate(snapshotDate.minusDays(30)),
                toDate(snapshotDate), toDate(snapshotDate.minusDays(1)), toDate(snapshotDate.minusDays(7)),
                snapshotDate, analysisProperties.getDecayLambda())) {
            Dish dish = dishMap.get(snapshot.getDishId());
            snapshot.setRefundQty30d(0);
            snapshot.setDecaySales30d(scale(snapshot.getDecaySales30d(), 4));
            snapshot.setIsHoliday(isWeekend(snapshotDate) ? 1 : 0);
            snapshot.setIsPromo(resolvePromotionFlag(dish, snapshotDate));
            snapshot.setWeatherCode(weatherCode);
            snapshot.setPrice(dish.getPrice() == null ? BigDecimal.ZERO : dish.getPrice());
            snapshot.setFeatureVersion(analysisProperties.getFeatureVersion());
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private String resolveWeatherCode(LocalDate snapshotDate) {
        if (snapshotDate == null) {
            return DEFAULT_WEATHER_CODE;
        }
        LocalDate today = LocalDate.now(APP_ZONE);
        long daysBetween = ChronoUnit.DAYS.between(snapshotDate, today);
        int historyDays = Math.max(1, analysisProperties.getExternal().getWeatherHistoryDays());
        if (daysBetween < 0 || (daysBetween > historyDays && !snapshotDate.isEqual(today))) {
            log.warn("[Weather-Degrade] Date: {}, Filled: {}", snapshotDate, DEFAULT_WEATHER_CODE);
            return DEFAULT_WEATHER_CODE;
        }
        String location = analysisProperties.getExternal().getWeatherLocation();
        if (!StringUtils.hasText(location)) {
            log.warn("[Weather-Degrade] Date: {}, Filled: {}", snapshotDate, DEFAULT_WEATHER_CODE);
            return DEFAULT_WEATHER_CODE;
        }
        String weatherCode = aiWeatherService.getWeatherCode(location, snapshotDate);
        if (!StringUtils.hasText(weatherCode)) {
            log.warn("[Weather-Degrade] Date: {}, Filled: {}", snapshotDate, DEFAULT_WEATHER_CODE);
            return DEFAULT_WEATHER_CODE;
        }
        return weatherCode;
    }

    private int resolvePromotionFlag(Dish dish, LocalDate snapshotDate) {
        if (dish == null || snapshotDate == null) {
            return 0;
        }
        return analysisProperties.getExternal().getPromoPeriods().stream()
                .filter(Objects::nonNull)
                .filter(period -> period.getStart() != null && period.getEnd() != null)
                .filter(period -> !snapshotDate.isBefore(period.getStart()) && !snapshotDate.isAfter(period.getEnd()))
                .anyMatch(period -> matchesPromotionScope(period, dish)) ? 1 : 0;
    }

    private boolean matchesPromotionScope(AnalysisProperties.PromoPeriodProperties period, Dish dish) {
        boolean dishScopeEmpty = period.getDishIds() == null || period.getDishIds().isEmpty();
        boolean categoryScopeEmpty = period.getCategoryIds() == null || period.getCategoryIds().isEmpty();
        if (dishScopeEmpty && categoryScopeEmpty) {
            return true;
        }
        if (!dishScopeEmpty && dish.getId() != null && period.getDishIds().contains(dish.getId())) {
            return true;
        }
        return !categoryScopeEmpty && dish.getCategoryId() != null && period.getCategoryIds().contains(dish.getCategoryId());
    }

    private AnalysisWindowData loadWindowData(LocalDate endExclusive, int lookbackDays) {
        Date beginTime = toDate(endExclusive.minusDays(lookbackDays));
        Date endTime = toDate(endExclusive);
        List<Orders> orders = ordersMapper.listCompletedInWindow(beginTime, endTime);
        if (orders == null || orders.isEmpty()) {
            return new AnalysisWindowData(Collections.emptyList(), Collections.emptyMap());
        }

        List<Long> orderIds = orders.stream().map(Orders::getId).filter(Objects::nonNull).toList();
        if (orderIds.isEmpty()) {
            return new AnalysisWindowData(Collections.emptyList(), Collections.emptyMap());
        }
        List<OrderDetail> details = orderDetailMapper.listDishDetailsForOrders(orderIds);
        if (details == null || details.isEmpty()) {
            return new AnalysisWindowData(Collections.emptyList(), orders.stream()
                    .filter(order -> order.getId() != null)
                    .collect(Collectors.toMap(Orders::getId, order -> order, (a, b) -> a)));
        }

        Map<Long, Orders> orderMap = orders.stream()
                .filter(order -> order != null && order.getId() != null)
                .collect(Collectors.toMap(Orders::getId, order -> order, (a, b) -> a));
        Set<Long> dishIds = details.stream().map(OrderDetail::getDishId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Dish> dishMap = dishIds.isEmpty() ? Collections.emptyMap() : dishService.listByIds(dishIds).stream()
                .filter(dish -> dish != null && dish.getId() != null)
                .collect(Collectors.toMap(Dish::getId, dish -> dish, (a, b) -> a));

        List<OrderContext> contexts = new ArrayList<>(details.size());
        for (OrderDetail detail : details) {
            Orders order = orderMap.get(detail.getOrderId());
            Dish dish = dishMap.get(detail.getDishId());
            if (order == null || dish == null) {
                continue;
            }
            contexts.add(new OrderContext(order, detail, dish));
        }
        return new AnalysisWindowData(contexts, orderMap);
    }

    private Map<Integer, List<Long>> buildClusterTopDishMap(
            LocalDate snapshotDate,
            List<UserBehaviorFeatureSnapshot> snapshots,
            List<Integer> assignments
    ) {
        Map<Long, Integer> userClusterMap = new HashMap<>(snapshots.size());
        for (int i = 0; i < snapshots.size(); i++) {
            userClusterMap.put(snapshots.get(i).getUserId(), assignments.get(i));
        }

        List<Long> fallbackDishIds = listRecommendedDishIdsForUser(null, analysisProperties.getColdStart().getTopN());
        Map<Integer, List<Long>> topDishMap = new HashMap<>();
        Set<Integer> clusterIds = new HashSet<>(assignments);
        for (Integer clusterId : clusterIds) {
            List<Long> userIds = userClusterMap.entrySet().stream().filter(e -> e.getValue().equals(clusterId))
                    .map(Map.Entry::getKey).toList();
            List<Long> topDishIds = orderDetailMapper.aggregateAnalysisSales(toDate(snapshotDate.minusDays(30)),
                    toDate(snapshotDate), userIds, null, Math.max(1, analysisProperties.getColdStart().getTopN()))
                    .stream().map(com.codeying.vo.admin.analysis.AnalysisDishSales::getDishId).toList();
            if (topDishIds.isEmpty()) {
                topDishIds = fallbackDishIds;
            }
            topDishMap.put(clusterId, topDishIds);
        }
        return topDishMap;
    }

    private Map<String, Object> logHeatValidation(LocalDate snapshotDate) {
        HeatDataset dataset = buildHeatDataset(snapshotDate);
        HeatDatasetSplit split = splitHeatDataset(dataset);
        if (split.evalSamples().isEmpty()) {
            log.warn("Heat validation skipped: no eval samples, snapshotDate={}", snapshotDate);
            return Map.of();
        }

        HeatModelStore.Loaded loaded = modelStore.load();
        Booster booster = loaded == null ? null : loaded.booster();
        if (booster == null) {
            log.warn("Heat validation skipped: model not found, snapshotDate={}", snapshotDate);
            return Map.of();
        }

        try {
        double squaredError = 0D;
        double absoluteError = 0D;
        double averageActual = 0D;
        int sampleCount = 0;
        for (HeatTrainingSample sample : split.evalSamples()) {
            double actual = sample.label();
            double predicted = predictWindowSales(sample.features(), booster);
            squaredError += Math.pow(predicted - actual, 2);
            absoluteError += Math.abs(predicted - actual);
            averageActual += actual;
            sampleCount++;
        }
        if (sampleCount == 0) {
            log.warn("Heat validation skipped: sampleCount is 0, snapshotDate={}", snapshotDate);
            return Map.of();
        }

        double rmse = Math.sqrt(squaredError / sampleCount);
        double mae = absoluteError / sampleCount;
        double averageWindowSales = averageActual / sampleCount;
        double rmseRatio = averageWindowSales <= 0 ? rmse : rmse / averageWindowSales;
        double maeRatio = averageWindowSales <= 0 ? mae : mae / averageWindowSales;
        log.info("Heat validation done, snapshotDate={}, rmse={}, mae={}, rmseRatio={}, maeRatio={}",
                snapshotDate,
                scale(rmse, 4),
                scale(mae, 4),
                scale(rmseRatio, 6),
                scale(maeRatio, 6));
        if (rmseRatio > analysisProperties.getValidation().getRmseRatioThreshold()) {
            log.warn("RMSE above threshold, snapshotDate={}, rmseRatio={}, threshold={}",
                    snapshotDate,
                    scale(rmseRatio, 6),
                    analysisProperties.getValidation().getRmseRatioThreshold());
        }
        if (maeRatio > analysisProperties.getValidation().getMaeRatioThreshold()) {
            log.warn("MAE above threshold, snapshotDate={}, maeRatio={}, threshold={}",
                    snapshotDate,
                    scale(maeRatio, 6),
                    analysisProperties.getValidation().getMaeRatioThreshold());
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("snapshotDate", snapshotDate.toString());
        metrics.put("modelVersion", loaded.version());
        metrics.put("evalWindowStart", split.evalSamples().get(0).snapshotDate().toString());
        metrics.put("evalWindowEnd", split.evalSamples().get(split.evalSamples().size() - 1).snapshotDate().toString());
        metrics.put("sampleCount", sampleCount);
        metrics.put("averageWindowSales", scale(averageWindowSales, 4));
        metrics.put("rmse", scale(rmse, 4));
        metrics.put("mae", scale(mae, 4));
        metrics.put("rmseRatio", scale(rmseRatio, 6));
        metrics.put("maeRatio", scale(maeRatio, 6));
        metrics.put("rmseThreshold", analysisProperties.getValidation().getRmseRatioThreshold());
        metrics.put("maeThreshold", analysisProperties.getValidation().getMaeRatioThreshold());
        metrics.put("rmsePass", rmseRatio <= analysisProperties.getValidation().getRmseRatioThreshold());
        metrics.put("maePass", maeRatio <= analysisProperties.getValidation().getMaeRatioThreshold());
        writeCache(RedisKeys.analysisOfflineValidationKey(snapshotDate.toString()), writeJson(metrics), 240);
        return metrics;
        } finally { loaded.close(); }
    }
    private String resolveCurrentModelVersion(LocalDate snapshotDate) {
        DishHeatPredictionResult result = dishHeatPredictionResultMapper.findLatestActive(normalizeDate(snapshotDate));
        if (result == null || !StringUtils.hasText(result.getModelVersion())) {
            return "unknown";
        }
        return result.getModelVersion();
    }
    private ClusterCachePayload readClusterPayload(Long userId) {
        String cached = stringRedisTemplate.opsForValue().get(RedisKeys.userClusterKey(userId));
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, ClusterCachePayload.class);
            } catch (Exception ex) {
                log.warn("Read user cluster cache unavailable: {}", ex.getClass().getSimpleName());
            }
        }

        UserClusterResult result = userClusterResultMapper.findLatestForUser(userId);
        if (result == null) {
            return null;
        }
        ClusterCachePayload payload = new ClusterCachePayload(result.getClusterId(), readLongList(result.getTopnDishJson()));
        writeCache(
                RedisKeys.userClusterKey(userId),
                writeJson(payload),
                analysisProperties.getRedis().getClusterUserTtlHours()
        );
        return payload;
    }

    private List<Long> listDistrictHotDishIds(Long userId, Integer limit) {
        AddressBook targetAddress = addressBookMapper.findRecommendationDistrict(userId);
        if (targetAddress == null) {
            return Collections.emptyList();
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        return orderDetailMapper.aggregateAnalysisSales(toDate(today.minusDays(30)), toDate(today), null,
                targetAddress.getDistrictName(), limit).stream()
                .map(com.codeying.vo.admin.analysis.AnalysisDishSales::getDishId).toList();
    }

    private Map<Long, Double> buildCategoryMeanMap(
            List<DishTimeseriesFeatureSnapshot> snapshots,
            LocalDate targetDate,
            Booster booster
    ) {
        Map<Long, List<Double>> grouped = new HashMap<>();
        for (DishTimeseriesFeatureSnapshot snapshot : snapshots) {
            if (snapshot.getCategoryId() == null) {
                continue;
            }
            grouped.computeIfAbsent(snapshot.getCategoryId(), ignored -> new ArrayList<>())
                    .add(predictWindowSales(snapshot, targetDate, booster));
        }
        Map<Long, Double> result = new HashMap<>();
        for (Map.Entry<Long, List<Double>> entry : grouped.entrySet()) {
            double mean = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0D);
            result.put(entry.getKey(), mean);
        }
        return result;
    }

    private Booster trainHeatModel(LocalDate snapshotDate) {
        // With no observed completed dish history, retained synthetic zero labels cannot establish training readiness.
        if (orderDetailMapper.aggregateAnalysisSales(toDate(snapshotDate.minusDays(analysisProperties.getTrainingLookbackDays() + 30L)),
                toDate(snapshotDate), null, null, null).isEmpty()) return null;
        HeatDataset dataset = buildHeatDataset(snapshotDate);
        HeatDatasetSplit split = splitHeatDataset(dataset);
        if (split.trainSamples().isEmpty()) {
            log.warn("XGBoost training skipped: train samples are empty, snapshotDate={}", snapshotDate);
            return null;
        }

        List<DMatrix> matrices = new ArrayList<>();
        Booster trained = null;
        boolean saved = false;
        try {
            DMatrix trainMatrix = toDMatrix(split.trainSamples(), true);
            matrices.add(trainMatrix);
            Map<String, Object> params = new HashMap<>();
            params.put("objective", "reg:squarederror");
            params.put("eval_metric", "rmse");
            params.put("eta", analysisProperties.getXgboost().getEta());
            params.put("max_depth", analysisProperties.getXgboost().getMaxDepth());
            params.put("subsample", analysisProperties.getXgboost().getSubsample());
            params.put("colsample_bytree", analysisProperties.getXgboost().getColsampleBytree());
            params.put("min_child_weight", analysisProperties.getXgboost().getMinChildWeight());
            params.put("missing", MISSING_VALUE);
            params.put("seed", 20260418);

            Map<String, DMatrix> watches = new LinkedHashMap<>();
            watches.put("train", trainMatrix);
            if (!split.evalSamples().isEmpty()) {
                DMatrix evalMatrix = toDMatrix(split.evalSamples(), true);
                matrices.add(evalMatrix);
                watches.put("eval", evalMatrix);
            }

            Booster booster = XGBoost.train(
                    trainMatrix,
                    params,
                    Math.max(1, analysisProperties.getXgboost().getRounds()),
                    watches,
                    null,
                    null
            );

            trained = booster;
            String modelVersion = buildModelVersion("heat-xgb", snapshotDate);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("modelVersion", modelVersion);
            meta.put("featureVersion", analysisProperties.getFeatureVersion());
            meta.put("trainedAt", LocalDateTime.now(APP_ZONE).toString());
            meta.put("snapshotDate", snapshotDate.toString());
            meta.put("trainSampleCount", split.trainSamples().size());
            meta.put("evalSampleCount", split.evalSamples().size());
            meta.put("longTailSalesThreshold", analysisProperties.getXgboost().getLongTailSalesThreshold());
            meta.put("zeroLabelKeepRate", analysisProperties.getXgboost().getZeroLabelKeepRate());
            meta.put("enableLogLabel", analysisProperties.getXgboost().getEnableLogLabel());
            modelStore.save(booster, meta);

            log.info("XGBoost heat training done, snapshotDate={}, modelVersion={}, trainSamples={}, evalSamples={}, modelPath={}",
                    snapshotDate, modelVersion, split.trainSamples().size(), split.evalSamples().size(), modelStore.metadata().get("modelFile"));
            saved = true;
            return booster;
        } catch (XGBoostError ex) {
            throw new BusinessException("热度模型训练失败", ex);
        } finally {
            matrices.forEach(DMatrix::dispose);
            if (!saved && trained != null) trained.dispose();
        }
    }

    private HeatDataset buildHeatDataset(LocalDate targetDate) {
        int predictionWindowDays = Math.max(1, analysisProperties.getPredictionWindowDays());
        LocalDate startDate = targetDate.minusDays(analysisProperties.getTrainingLookbackDays());
        LocalDate lastSampleDate = targetDate.minusDays(predictionWindowDays);
        if (lastSampleDate.isBefore(startDate)) {
            return new HeatDataset(Collections.emptyList());
        }

        List<Dish> dishes = dishService.list().stream()
                .filter(dish -> dish != null && dish.getId() != null)
                .toList();
        if (dishes.isEmpty()) {
            return new HeatDataset(Collections.emptyList());
        }

        List<HeatTrainingSample> samples = new ArrayList<>();
        for (LocalDate sampleDate = startDate; !sampleDate.isAfter(lastSampleDate); sampleDate = sampleDate.plusDays(1)) {
            AnalysisWindowData historyWindow = loadWindowData(sampleDate, 30);
            List<DishTimeseriesFeatureSnapshot> snapshots = buildDishSnapshots(sampleDate, dishes, historyWindow);
            Map<Long, Integer> futureSalesMap = loadDishWindowSales(sampleDate, predictionWindowDays);
            for (DishTimeseriesFeatureSnapshot snapshot : snapshots) {
                int label = futureSalesMap.getOrDefault(snapshot.getDishId(), 0);
                if (!heatFeatures.shouldKeepHeatSample(snapshot, sampleDate, label)) {
                    continue;
                }
                samples.add(new HeatTrainingSample(
                        sampleDate,
                        snapshot.getDishId(),
                        heatFeatures.toHeatFeatures(snapshot, sampleDate),
                        label,
                        heatFeatures.resolveHeatSampleWeight(snapshot, label)
                ));
            }
        }
        return new HeatDataset(samples);
    }

    private HeatDatasetSplit splitHeatDataset(HeatDataset dataset) {
        if (dataset.samples().isEmpty()) {
            return new HeatDatasetSplit(Collections.emptyList(), Collections.emptyList());
        }

        List<LocalDate> snapshotDates = dataset.samples().stream()
                .map(HeatTrainingSample::snapshotDate)
                .distinct()
                .sorted()
                .toList();
        if (snapshotDates.size() == 1) {
            return new HeatDatasetSplit(dataset.samples(), Collections.emptyList());
        }

        int holdoutDays = Math.max(1, analysisProperties.getXgboost().getEvalHoldoutDays());
        int evalDateCount = Math.min(holdoutDays, snapshotDates.size() - 1);
        LocalDate evalStartDate = snapshotDates.get(snapshotDates.size() - evalDateCount);

        List<HeatTrainingSample> trainSamples = dataset.samples().stream()
                .filter(sample -> sample.snapshotDate().isBefore(evalStartDate))
                .toList();
        List<HeatTrainingSample> evalSamples = dataset.samples().stream()
                .filter(sample -> !sample.snapshotDate().isBefore(evalStartDate))
                .toList();
        if (trainSamples.isEmpty()) {
            return new HeatDatasetSplit(dataset.samples(), Collections.emptyList());
        }
        return new HeatDatasetSplit(trainSamples, evalSamples);
    }

    private Map<Long, Integer> loadDishWindowSales(LocalDate windowStart, int windowDays) {
        return orderDetailMapper.aggregateAnalysisSales(toDate(windowStart), toDate(windowStart.plusDays(windowDays)),
                null, null, null).stream().collect(Collectors.toMap(
                com.codeying.vo.admin.analysis.AnalysisDishSales::getDishId,
                com.codeying.vo.admin.analysis.AnalysisDishSales::getQuantity));
    }

    private DMatrix toDMatrix(List<HeatTrainingSample> samples, boolean withLabel) throws XGBoostError {
        float[] data = new float[samples.size() * HEAT_FEATURE_COUNT];
        float[] labels = withLabel ? new float[samples.size()] : null;
        float[] weights = withLabel ? new float[samples.size()] : null;
        int offset = 0;
        for (int index = 0; index < samples.size(); index++) {
            HeatTrainingSample sample = samples.get(index);
            System.arraycopy(sample.features(), 0, data, offset, sample.features().length);
            offset += sample.features().length;
            if (withLabel) {
                labels[index] = heatFeatures.transformHeatLabel(sample.label());
                weights[index] = sample.sampleWeight();
            }
        }
        DMatrix matrix = new DMatrix(data, samples.size(), HEAT_FEATURE_COUNT, MISSING_VALUE);
        try {
            if (withLabel) {
                matrix.setLabel(labels);
                matrix.setWeight(weights);
            }
            return matrix;
        } catch (XGBoostError | RuntimeException ex) {
            matrix.dispose();
            throw ex;
        }
    }

    private double predictWindowSales(DishTimeseriesFeatureSnapshot snapshot, LocalDate targetDate, Booster booster) {
        return predictWindowSales(heatFeatures.toHeatFeatures(snapshot, targetDate), booster);
    }

    private double predictWindowSales(float[] features, Booster booster) {
        if (booster == null) {
            return heatFeatures.forecastWindowSalesBaseline(features[0], features[1], features[2], features[3], (int) features[17]);
        }
        DMatrix matrix = null;
        try {
            matrix = new DMatrix(features, 1, HEAT_FEATURE_COUNT, MISSING_VALUE);
            float[][] predicted = booster.predict(matrix);
            if (predicted.length == 0 || predicted[0].length == 0 || !Float.isFinite(predicted[0][0])) {
                throw new BusinessException("热度模型返回无效预测");
            }
            return heatFeatures.restoreHeatLabel(predicted[0][0]);
        } catch (XGBoostError ex) {
            throw new BusinessException("热度预测失败", ex);
        } finally { if (matrix != null) matrix.dispose(); }
    }

    private String buildModelVersion(String module, LocalDate date) {
        return analysisProperties.getModelVersionPrefix() + "-" + module + "-" + date + "-" + System.currentTimeMillis();
    }

    private void writeCache(String key, String value, Integer ttlHours) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value) || ttlHours == null || ttlHours <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key, value, ttlHours, TimeUnit.HOURS);
    }

    private List<Long> readLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception ex) {
            log.warn("Parse Long list failed, causeType={}", ex.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException("分析结果序列化失败", ex);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 100);
    }

    private LocalDate normalizeDate(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now(APP_ZONE) : date;
        if (target.getYear() < 1000 || target.getYear() > 9998) throw new BusinessException("分析日期超出支持范围");
        return target;
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(APP_ZONE).toInstant());
    }

    private boolean isWeekend(LocalDate date) {
        if (date == null) {
            return false;
        }
        return date.getDayOfWeek().getValue() >= 6;
    }

    private int quantity(OrderDetail detail) {
        return detail == null || detail.getNumber() == null ? 1 : Math.max(1, detail.getNumber());
    }

    private double valueOrZero(Integer value) {
        return value == null ? 0D : value.doubleValue();
    }

    private double decimal(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }

    private double percentage(Integer numerator, double denominator) {
        if (numerator == null || denominator <= 0D) {
            return 0D;
        }
        return numerator / denominator;
    }

    private BigDecimal scale(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private record OrderContext(Orders order, OrderDetail detail, Dish dish) {}

    private record AnalysisWindowData(List<OrderContext> contexts, Map<Long, Orders> orderMap) {}


    private record HeatTrainingSample(LocalDate snapshotDate, Long dishId, float[] features, float label, float sampleWeight) {}

    private record HeatDataset(List<HeatTrainingSample> samples) {}

    private record HeatDatasetSplit(List<HeatTrainingSample> trainSamples, List<HeatTrainingSample> evalSamples) {}

    private record ClusterCachePayload(Integer clusterId, List<Long> topDishIds) {}

    /** Flavor token encoding is pure feature math; order/sales/business counts are Mapper SQL. */
    private final class UserFeatureAggregation {
        private final Map<String, Integer> flavorCountMap = new HashMap<>();
        private void consume(OrderContext context, LocalDate date, List<DishFlavor> definitions) {
            List<String> tokens = tokenizeFlavors(context.detail().getDishFlavor());
            if (tokens.isEmpty()) for (DishFlavor flavor : definitions) tokens.addAll(tokenizeFlavors(flavor.getValue()));
            for (String token : tokens) flavorCountMap.merge(token, quantity(context.detail()), Integer::sum);
        }
        private Map<String, BigDecimal> flavorVector() {
            int total = flavorCountMap.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) return Collections.emptyMap();
            Map<String, BigDecimal> vector = new LinkedHashMap<>();
            flavorCountMap.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(10)
                    .forEach(entry -> vector.put(entry.getKey(), BigDecimal.valueOf(entry.getValue())
                            .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)));
            return vector;
        }
    }

    private List<String> tokenizeFlavors(String rawFlavor) {
        if (!StringUtils.hasText(rawFlavor)) {
            return new ArrayList<>();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(rawFlavor);
        while (matcher.find()) {
            String token = matcher.group();
            if (StringUtils.hasText(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return LocalDateTime.of(LocalDate.now(APP_ZONE), LocalTime.MIDNIGHT);
        }
        return LocalDateTime.ofInstant(date.toInstant(), APP_ZONE);
    }
}
