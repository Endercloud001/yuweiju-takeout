package com.codeying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.codeying.service.AddressBookService;
import com.codeying.service.AiWeatherService;
import com.codeying.service.AnalysisApplicationService;
import com.codeying.service.AnalysisObservationService;
import com.codeying.service.DishFlavorService;
import com.codeying.service.DishService;
import com.codeying.service.OrderDetailService;
import com.codeying.service.OrdersService;
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

    private final OrdersService ordersService;
    private final OrderDetailService orderDetailService;
    private final DishService dishService;
    private final DishFlavorService dishFlavorService;
    private final UserService userService;
    private final AddressBookService addressBookService;
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
            OrdersService ordersService,
            OrderDetailService orderDetailService,
            DishService dishService,
            DishFlavorService dishFlavorService,
            UserService userService,
            AddressBookService addressBookService,
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
        this.ordersService = ordersService;
        this.orderDetailService = orderDetailService;
        this.dishService = dishService;
        this.dishFlavorService = dishFlavorService;
        this.userService = userService;
        this.addressBookService = addressBookService;
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
        saveUserSnapshots(targetDate, users, flavorMap, window30);
        saveDishSnapshots(targetDate, dishes, window30);

        log.info("Generate daily snapshots done, snapshotDate={}, userCount={}, dishCount={}", targetDate, users.size(), dishes.size());
    }

    @Override
    public WeeklyTrainingResult runWeeklyTraining(LocalDate snapshotDate) {
        LocalDate targetDate = normalizeDate(snapshotDate);
        List<UserBehaviorFeatureSnapshot> snapshots = userBehaviorFeatureSnapshotMapper.selectList(new QueryWrapper<UserBehaviorFeatureSnapshot>()
                .eq("snapshot_date", targetDate)
                .eq("feature_version", analysisProperties.getFeatureVersion())
                .orderByAsc("user_id"));
        if (snapshots == null || snapshots.isEmpty()) {
            log.warn("Weekly training skipped: user snapshots are empty, snapshotDate={}", targetDate);
            return new WeeklyTrainingResult(
                    targetDate,
                    false,
                    0,
                    0,
                    null,
                    false,
                    "NO_USER_SNAPSHOTS"
            );
        }

        KMeansResult kMeansResult = buildKMeansResult(snapshots);
        Map<Integer, List<Long>> clusterTopDishMap = buildClusterTopDishMap(targetDate, snapshots, kMeansResult.assignments());
        String modelVersion = buildModelVersion("cluster", targetDate);
        boolean heatModelTrained = false;

        if (Boolean.TRUE.equals(analysisProperties.getXgboost().getEnabled())) {
            heatModelTrained = trainHeatModel(targetDate) != null;
        } else {
            log.warn("XGBoost is disabled, skip heat model training, snapshotDate={}", targetDate);
        }

        userClusterResultMapper.delete(new QueryWrapper<UserClusterResult>().eq("snapshot_date", targetDate));
        for (int i = 0; i < snapshots.size(); i++) {
            UserBehaviorFeatureSnapshot snapshot = snapshots.get(i);
            Integer clusterId = kMeansResult.assignments().get(i);
            List<Long> clusterTopDishIds = clusterTopDishMap.getOrDefault(clusterId, Collections.emptyList());

            UserClusterResult result = new UserClusterResult();
            result.setSnapshotDate(targetDate);
            result.setUserId(snapshot.getUserId());
            result.setClusterId(clusterId);
            result.setClusterScore(scale(kMeansResult.confidences().get(i), 6));
            result.setModelVersion(modelVersion);
            result.setFeatureVersion(analysisProperties.getFeatureVersion());
            result.setTopnDishJson(writeJson(clusterTopDishIds));
            userClusterResultMapper.insert(result);

            ClusterCachePayload payload = new ClusterCachePayload(clusterId, clusterTopDishIds);
            writeCache(
                    RedisKeys.userClusterKey(snapshot.getUserId()),
                    writeJson(payload),
                    analysisProperties.getRedis().getClusterUserTtlHours()
            );
        }
        for (Map.Entry<Integer, List<Long>> entry : clusterTopDishMap.entrySet()) {
            writeCache(
                    RedisKeys.clusterTopnKey(entry.getKey()),
                    writeJson(entry.getValue()),
                    analysisProperties.getRedis().getClusterTopnTtlHours()
            );
        }

        log.info(
                "Weekly clustering done, snapshotDate={}, modelVersion={}, clusterCount={}, silhouette={}",
                targetDate,
                modelVersion,
                clusterTopDishMap.size(),
                scale(kMeansResult.silhouette(), 6)
        );
        if (kMeansResult.silhouette() < analysisProperties.getValidation().getSilhouetteMin()) {
            log.warn(
                    "Clustering quality below threshold, snapshotDate={}, silhouette={}, threshold={}",
                    targetDate,
                    scale(kMeansResult.silhouette(), 6),
                    analysisProperties.getValidation().getSilhouetteMin()
            );
        }

        logHeatValidation(targetDate);
        analysisObservationService.logOnlineObservation(targetDate, resolveCurrentModelVersion(targetDate));
        return new WeeklyTrainingResult(
                targetDate,
                true,
                snapshots.size(),
                clusterTopDishMap.size(),
                modelVersion,
                heatModelTrained,
                null
        );
    }

    @Override
    public void runDailyPrediction(LocalDate windowStart) {
        LocalDate targetDate = normalizeDate(windowStart);
        LocalDate windowEnd = targetDate.plusDays(Math.max(1, analysisProperties.getPredictionWindowDays()) - 1L);
        List<DishTimeseriesFeatureSnapshot> snapshots = dishTimeseriesFeatureSnapshotMapper.selectList(new QueryWrapper<DishTimeseriesFeatureSnapshot>()
                .eq("snapshot_date", targetDate)
                .eq("feature_version", analysisProperties.getFeatureVersion()));
        if (snapshots == null || snapshots.isEmpty()) {
            log.warn("Daily prediction skipped: dish snapshots are empty, windowStart={}", targetDate);
            return;
        }

        Map<Long, Dish> dishMap = dishService.list().stream()
                .filter(dish -> dish != null && dish.getId() != null)
                .collect(Collectors.toMap(Dish::getId, dish -> dish, (a, b) -> a));
        Booster heatBooster = loadHeatModel();
        Map<Long, Double> categoryMeanMap = buildCategoryMeanMap(snapshots, targetDate, heatBooster);
        double globalMean = categoryMeanMap.values().stream().mapToDouble(Double::doubleValue).average().orElse(1D);
        int smoothingM = Math.max(1, analysisProperties.getColdStart().getBayesianSmoothingM());
        if (heatBooster == null && Boolean.TRUE.equals(analysisProperties.getXgboost().getEnabled())) {
            log.warn("Heat model not loaded, fallback to on-demand XGBoost training, windowStart={}", targetDate);
            heatBooster = trainHeatModel(targetDate);
        }
        String modelVersion = resolveHeatModelVersion(targetDate);

        dishHeatPredictionResultMapper.delete(new QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", targetDate)
                .eq("window_end", windowEnd));

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
            explain.put("longTail", isLongTailSnapshot(snapshot));
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
            dishHeatPredictionResultMapper.insert(result);
            results.add(result);

            writeCache(
                    RedisKeys.dishHeatPredictionKey(targetDate.toString(), snapshot.getDishId()),
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

        log.info("Daily prediction done, windowStart={}, windowEnd={}, modelVersion={}, resultCount={}",
                targetDate, windowEnd, modelVersion, results.size());
    }

    @Override
    public void backfillSnapshots(LocalDate endDate, Integer days) {
        LocalDate targetEndDate = normalizeDate(endDate);
        int totalDays = days == null || days <= 0 ? analysisProperties.getBackfillDays() : days;
        log.info("Backfill snapshots start, endDate={}, days={}", targetEndDate, totalDays);
        for (int i = 0; i < totalDays; i++) {
            LocalDate snapshotDate = targetEndDate.minusDays(i);
            try {
                generateDailySnapshots(snapshotDate);
            } catch (Exception ex) {
                log.warn("Backfill one day failed, snapshotDate={}, error={}", snapshotDate, ex.getMessage(), ex);
            }
        }
        log.info("Backfill snapshots done, endDate={}, days={}", targetEndDate, totalDays);
    }

    @Override
    public List<DishHeatPredictionVO> listHeatPredictions(LocalDate windowStart, Integer limit) {
        LocalDate targetDate = normalizeDate(windowStart);
        int size = normalizeLimit(limit);
        List<DishHeatPredictionResult> results = dishHeatPredictionResultMapper.selectList(new QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", targetDate)
                .eq("status", 1)
                .orderByDesc("heat_score")
                .last("limit " + size));
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
        List<UserClusterResult> results = userClusterResultMapper.selectList(new QueryWrapper<UserClusterResult>()
                .eq("snapshot_date", targetDate)
                .orderByAsc("cluster_id")
                .orderByAsc("user_id")
                .last("limit " + size));
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
        List<DishHeatPredictionResult> heatResults = dishHeatPredictionResultMapper.selectList(new QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", today)
                .eq("status", 1)
                .orderByDesc("heat_score")
                .last("limit " + size));
        if (heatResults != null && !heatResults.isEmpty()) {
            return heatResults.stream().map(DishHeatPredictionResult::getDishId).filter(Objects::nonNull).toList();
        }

        AnalysisWindowData windowData = loadWindowData(today, 30);
        return windowData.contexts().stream()
                .collect(Collectors.groupingBy(ctx -> ctx.detail().getDishId(), Collectors.summingInt(ctx -> quantity(ctx.detail()))))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(size)
                .toList();
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
            log.warn("Read offline validation metrics failed, snapshotDate={}, error={}", targetDate, ex.getMessage());
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
        userBehaviorFeatureSnapshotMapper.delete(new QueryWrapper<UserBehaviorFeatureSnapshot>()
                .eq("snapshot_date", snapshotDate)
                .eq("feature_version", featureVersion));

        Map<Long, UserFeatureAggregation> userAggregationMap = new HashMap<>();
        for (OrderContext context : window30.contexts()) {
            Long userId = context.order().getUserId();
            if (userId == null) {
                continue;
            }
            UserFeatureAggregation aggregation = userAggregationMap.computeIfAbsent(userId, ignored -> new UserFeatureAggregation());
            aggregation.consume(context, snapshotDate, flavorMap.getOrDefault(context.detail().getDishId(), Collections.emptyList()));
        }

        for (User user : users) {
            UserFeatureAggregation aggregation = userAggregationMap.getOrDefault(user.getId(), new UserFeatureAggregation());
            UserBehaviorFeatureSnapshot snapshot = new UserBehaviorFeatureSnapshot();
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setUserId(user.getId());
            snapshot.setRfmRecencyDays(aggregation.recencyDays(snapshotDate));
            snapshot.setRfmFrequency30d(aggregation.orderCount());
            snapshot.setRfmMonetary30d(scale(aggregation.monetary(), 2));
            snapshot.setOrderLunchRatio(scale(aggregation.mealRatio(MealPeriod.LUNCH), 4));
            snapshot.setOrderDinnerRatio(scale(aggregation.mealRatio(MealPeriod.DINNER), 4));
            snapshot.setOrderNightRatio(scale(aggregation.mealRatio(MealPeriod.NIGHT), 4));
            snapshot.setPriceLowRatio(scale(aggregation.priceRatio(PriceBand.LOW), 4));
            snapshot.setPriceMidRatio(scale(aggregation.priceRatio(PriceBand.MID), 4));
            snapshot.setPriceHighRatio(scale(aggregation.priceRatio(PriceBand.HIGH), 4));
            snapshot.setCategoryPrefTop1(aggregation.topCategory(0));
            snapshot.setCategoryPrefTop2(aggregation.topCategory(1));
            snapshot.setFlavorVectorJson(writeJson(aggregation.flavorVector()));
            snapshot.setFeatureVersion(featureVersion);
            userBehaviorFeatureSnapshotMapper.insert(snapshot);
        }
    }

    private void saveDishSnapshots(LocalDate snapshotDate, List<Dish> dishes, AnalysisWindowData window30) {
        String featureVersion = analysisProperties.getFeatureVersion();
        dishTimeseriesFeatureSnapshotMapper.delete(new QueryWrapper<DishTimeseriesFeatureSnapshot>()
                .eq("snapshot_date", snapshotDate)
                .eq("feature_version", featureVersion));
        for (DishTimeseriesFeatureSnapshot snapshot : buildDishSnapshots(snapshotDate, dishes, window30)) {
            dishTimeseriesFeatureSnapshotMapper.insert(snapshot);
        }
    }

    private List<DishTimeseriesFeatureSnapshot> buildDishSnapshots(
            LocalDate snapshotDate,
            List<Dish> dishes,
            AnalysisWindowData window30
    ) {
        Map<Long, DishFeatureAggregation> dishAggregationMap = new HashMap<>();
        for (OrderContext context : window30.contexts()) {
            Long dishId = context.detail().getDishId();
            if (dishId == null) {
                continue;
            }
            DishFeatureAggregation aggregation = dishAggregationMap.computeIfAbsent(dishId, ignored -> new DishFeatureAggregation());
            aggregation.consume(context, snapshotDate, analysisProperties.getDecayLambda());
        }

        String weatherCode = resolveWeatherCode(snapshotDate);
        List<DishTimeseriesFeatureSnapshot> snapshots = new ArrayList<>(dishes.size());
        for (Dish dish : dishes) {
            DishFeatureAggregation aggregation = dishAggregationMap.getOrDefault(dish.getId(), new DishFeatureAggregation());
            DishTimeseriesFeatureSnapshot snapshot = new DishTimeseriesFeatureSnapshot();
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setDishId(dish.getId());
            snapshot.setCategoryId(dish.getCategoryId());
            snapshot.setSalesQty1d(aggregation.salesQty1d());
            snapshot.setSalesQty7d(aggregation.salesQty7d());
            snapshot.setSalesQty30d(aggregation.salesQty30d());
            snapshot.setRefundQty30d(0);
            snapshot.setDecaySales30d(scale(aggregation.decaySales30d(), 4));
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
        List<Orders> orders = ordersService.list(new QueryWrapper<Orders>()
                .eq("status", Orders.COMPLETED)
                .ge("order_time", beginTime)
                .lt("order_time", endTime));
        if (orders == null || orders.isEmpty()) {
            return new AnalysisWindowData(Collections.emptyList(), Collections.emptyMap());
        }

        List<Long> orderIds = orders.stream().map(Orders::getId).filter(Objects::nonNull).toList();
        if (orderIds.isEmpty()) {
            return new AnalysisWindowData(Collections.emptyList(), Collections.emptyMap());
        }
        List<OrderDetail> details = orderDetailService.list(new QueryWrapper<OrderDetail>()
                .in("order_id", orderIds)
                .isNotNull("dish_id"));
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

        AnalysisWindowData windowData = loadWindowData(snapshotDate, 30);
        Map<Integer, Map<Long, Integer>> clusterDishCountMap = new HashMap<>();
        for (OrderContext context : windowData.contexts()) {
            Integer clusterId = userClusterMap.get(context.order().getUserId());
            if (clusterId == null || context.detail().getDishId() == null) {
                continue;
            }
            Map<Long, Integer> dishCountMap = clusterDishCountMap.computeIfAbsent(clusterId, ignored -> new HashMap<>());
            dishCountMap.merge(context.detail().getDishId(), quantity(context.detail()), Integer::sum);
        }

        List<Long> fallbackDishIds = listRecommendedDishIdsForUser(null, analysisProperties.getColdStart().getTopN());
        Map<Integer, List<Long>> topDishMap = new HashMap<>();
        Set<Integer> clusterIds = new HashSet<>(assignments);
        for (Integer clusterId : clusterIds) {
            Map<Long, Integer> dishCountMap = clusterDishCountMap.getOrDefault(clusterId, Collections.emptyMap());
            List<Long> topDishIds = dishCountMap.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .limit(Math.max(1, analysisProperties.getColdStart().getTopN()))
                    .toList();
            if (topDishIds.isEmpty()) {
                topDishIds = fallbackDishIds;
            }
            topDishMap.put(clusterId, topDishIds);
        }
        return topDishMap;
    }

    private KMeansResult buildKMeansResult(List<UserBehaviorFeatureSnapshot> snapshots) {
        if (snapshots.size() == 1) {
            return new KMeansResult(List.of(0), List.of(1D), 0D);
        }

        double[][] features = new double[snapshots.size()][];
        for (int i = 0; i < snapshots.size(); i++) {
            UserBehaviorFeatureSnapshot snapshot = snapshots.get(i);
            features[i] = new double[]{
                    valueOrZero(snapshot.getRfmRecencyDays()),
                    valueOrZero(snapshot.getRfmFrequency30d()),
                    decimal(snapshot.getRfmMonetary30d()),
                    decimal(snapshot.getOrderLunchRatio()),
                    decimal(snapshot.getOrderDinnerRatio()),
                    decimal(snapshot.getOrderNightRatio()),
                    decimal(snapshot.getPriceLowRatio()),
                    decimal(snapshot.getPriceMidRatio()),
                    decimal(snapshot.getPriceHighRatio())
            };
        }

        double[][] normalized = zScore(features);
        int k = Math.max(1, Math.min(analysisProperties.getClusterCount(), normalized.length));
        List<double[]> centroids = initCentroids(normalized, k);
        int[] assignments = new int[normalized.length];
        for (int i = 0; i < assignments.length; i++) {
            assignments[i] = -1;
        }

        boolean changed = true;
        for (int iteration = 0; iteration < 30 && changed; iteration++) {
            changed = assignClusters(normalized, centroids, assignments);
            recomputeCentroids(normalized, centroids, assignments, k);
        }

        List<Integer> assignmentList = new ArrayList<>(assignments.length);
        List<Double> confidences = new ArrayList<>(assignments.length);
        for (int i = 0; i < assignments.length; i++) {
            int clusterId = assignments[i];
            assignmentList.add(clusterId);
            confidences.add(1D / (1D + euclideanDistance(normalized[i], centroids.get(clusterId))));
        }
        double silhouette = silhouetteScore(normalized, assignments, k);
        return new KMeansResult(assignmentList, confidences, silhouette);
    }

    private void logHeatValidation(LocalDate snapshotDate) {
        HeatDataset dataset = buildHeatDataset(snapshotDate);
        HeatDatasetSplit split = splitHeatDataset(dataset);
        if (split.evalSamples().isEmpty()) {
            log.warn("Heat validation skipped: no eval samples, snapshotDate={}", snapshotDate);
            return;
        }

        Booster booster = loadHeatModel();
        if (booster == null) {
            log.warn("Heat validation skipped: model not found, snapshotDate={}", snapshotDate);
            return;
        }

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
            return;
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
    }
    private boolean shouldKeepHeatSample(DishTimeseriesFeatureSnapshot snapshot, LocalDate sampleDate, int label) {
        if (snapshot == null || sampleDate == null) {
            return false;
        }
        boolean zeroFuture = label <= 0;
        boolean zeroHistory = valueOrZero(snapshot.getSalesQty30d()) <= analysisProperties.getXgboost().getLongTailSalesThreshold();
        boolean neutralContext = valueOrZero(snapshot.getIsPromo()) == 0
                && valueOrZero(snapshot.getIsHoliday()) == 0
                && isNeutralWeather(snapshot.getWeatherCode());
        if (!(zeroFuture && zeroHistory && neutralContext)) {
            return true;
        }
        int hash = Objects.hash(sampleDate, snapshot.getDishId(), snapshot.getCategoryId());
        double bucket = Math.floorMod(hash, 10_000) / 10_000D;
        return bucket < analysisProperties.getXgboost().getZeroLabelKeepRate();
    }

    private float resolveHeatSampleWeight(DishTimeseriesFeatureSnapshot snapshot, int label) {
        double weight = 1D;
        if (!isLongTailSnapshot(snapshot) || label > analysisProperties.getXgboost().getLongTailSalesThreshold()) {
            weight *= analysisProperties.getXgboost().getActiveSampleWeight();
        }
        if (valueOrZero(snapshot.getIsPromo()) == 1) {
            weight *= analysisProperties.getXgboost().getPromoSampleWeight();
        }
        if (isSevereWeather(snapshot.getWeatherCode())) {
            weight *= analysisProperties.getXgboost().getSevereWeatherSampleWeight();
        }
        return (float) Math.max(weight, 0.1D);
    }

    private boolean isLongTailSnapshot(DishTimeseriesFeatureSnapshot snapshot) {
        return snapshot == null || valueOrZero(snapshot.getSalesQty30d()) <= analysisProperties.getXgboost().getLongTailSalesThreshold();
    }

    private boolean isNeutralWeather(String weatherCode) {
        String normalizedWeatherCode = normalizeWeatherCode(weatherCode);
        return Objects.equals(DEFAULT_WEATHER_CODE, normalizedWeatherCode) || Objects.equals("CLEAR", normalizedWeatherCode);
    }

    private boolean isSevereWeather(String weatherCode) {
        String normalizedWeatherCode = normalizeWeatherCode(weatherCode);
        return Objects.equals("RAIN", normalizedWeatherCode)
                || Objects.equals("SNOW", normalizedWeatherCode)
                || Objects.equals("THUNDER", normalizedWeatherCode)
                || Objects.equals("WIND", normalizedWeatherCode);
    }

    private float transformHeatLabel(float rawLabel) {
        if (!Boolean.TRUE.equals(analysisProperties.getXgboost().getEnableLogLabel())) {
            return Math.max(rawLabel, 0F);
        }
        return (float) Math.log1p(Math.max(rawLabel, 0F));
    }

    private double restoreHeatLabel(float predictedLabel) {
        double safeValue = Math.max(predictedLabel, 0F);
        if (!Boolean.TRUE.equals(analysisProperties.getXgboost().getEnableLogLabel())) {
            return safeValue;
        }
        return Math.max(Math.expm1(safeValue), 0D);
    }

    private String resolveCurrentModelVersion(LocalDate snapshotDate) {
        DishHeatPredictionResult result = dishHeatPredictionResultMapper.selectOne(new QueryWrapper<DishHeatPredictionResult>()
                .eq("window_start", normalizeDate(snapshotDate))
                .eq("status", 1)
                .orderByDesc("id")
                .last("limit 1"));
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
                log.warn("Read user cluster cache failed, userId={}, error={}", userId, ex.getMessage());
            }
        }

        UserClusterResult result = userClusterResultMapper.selectOne(new QueryWrapper<UserClusterResult>()
                .eq("user_id", userId)
                .orderByDesc("snapshot_date")
                .orderByDesc("id")
                .last("limit 1"));
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
        List<AddressBook> userAddresses = addressBookService.list(new QueryWrapper<AddressBook>()
                .eq("user_id", userId)
                .orderByDesc("is_default")
                .orderByDesc("id"));
        if (userAddresses == null || userAddresses.isEmpty()) {
            return Collections.emptyList();
        }
        AddressBook targetAddress = userAddresses.stream()
                .filter(address -> StringUtils.hasText(address.getDistrictName()))
                .findFirst()
                .orElse(null);
        if (targetAddress == null) {
            return Collections.emptyList();
        }

        Map<Long, AddressBook> addressMap = addressBookService.list().stream()
                .filter(address -> address != null && address.getId() != null)
                .collect(Collectors.toMap(AddressBook::getId, address -> address, (a, b) -> a));
        AnalysisWindowData windowData = loadWindowData(LocalDate.now(APP_ZONE), 30);
        Map<Long, Integer> hotDishMap = new HashMap<>();
        for (OrderContext context : windowData.contexts()) {
            AddressBook address = addressMap.get(context.order().getAddressBookId());
            if (address == null || !Objects.equals(targetAddress.getDistrictName(), address.getDistrictName())) {
                continue;
            }
            hotDishMap.merge(context.detail().getDishId(), quantity(context.detail()), Integer::sum);
        }
        return hotDishMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .toList();
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
        HeatDataset dataset = buildHeatDataset(snapshotDate);
        HeatDatasetSplit split = splitHeatDataset(dataset);
        if (split.trainSamples().isEmpty()) {
            log.warn("XGBoost training skipped: train samples are empty, snapshotDate={}", snapshotDate);
            return null;
        }

        try {
            DMatrix trainMatrix = toDMatrix(split.trainSamples(), true);
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
                watches.put("eval", toDMatrix(split.evalSamples(), true));
            }

            Booster booster = XGBoost.train(
                    trainMatrix,
                    params,
                    Math.max(1, analysisProperties.getXgboost().getRounds()),
                    watches,
                    null,
                    null
            );

            Files.createDirectories(getHeatModelDirectory());
            Path modelPath = getHeatModelPath();
            booster.saveModel(modelPath.toString());

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
            Files.writeString(getHeatModelMetaPath(), writeJson(meta));

            log.info("XGBoost heat training done, snapshotDate={}, modelVersion={}, trainSamples={}, evalSamples={}, modelPath={}",
                    snapshotDate, modelVersion, split.trainSamples().size(), split.evalSamples().size(), modelPath);
            return booster;
        } catch (IOException | XGBoostError ex) {
            log.error("XGBoost heat training failed, snapshotDate={}, error={}", snapshotDate, ex.getMessage(), ex);
            return null;
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
                if (!shouldKeepHeatSample(snapshot, sampleDate, label)) {
                    continue;
                }
                samples.add(new HeatTrainingSample(
                        sampleDate,
                        snapshot.getDishId(),
                        toHeatFeatures(snapshot, sampleDate),
                        label,
                        resolveHeatSampleWeight(snapshot, label)
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
        AnalysisWindowData windowData = loadWindowData(windowStart.plusDays(windowDays), windowDays);
        Map<Long, Integer> salesMap = new HashMap<>();
        for (OrderContext context : windowData.contexts()) {
            Long dishId = context.detail().getDishId();
            if (dishId == null) {
                continue;
            }
            salesMap.merge(dishId, quantity(context.detail()), Integer::sum);
        }
        return salesMap;
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
                labels[index] = transformHeatLabel(sample.label());
                weights[index] = sample.sampleWeight();
            }
        }
        DMatrix matrix = new DMatrix(data, samples.size(), HEAT_FEATURE_COUNT, MISSING_VALUE);
        if (withLabel) {
            matrix.setLabel(labels);
            matrix.setWeight(weights);
        }
        return matrix;
    }

    private double predictWindowSales(DishTimeseriesFeatureSnapshot snapshot, LocalDate targetDate, Booster booster) {
        return predictWindowSales(toHeatFeatures(snapshot, targetDate), booster);
    }

    private double predictWindowSales(float[] features, Booster booster) {
        if (booster == null) {
            return forecastWindowSalesBaseline(features[0], features[1], features[2], features[3], (int) features[17]);
        }
        try {
            DMatrix matrix = new DMatrix(features, 1, HEAT_FEATURE_COUNT, MISSING_VALUE);
            float[][] predicted = booster.predict(matrix);
            if (predicted.length == 0 || predicted[0].length == 0) {
                return 0D;
            }
            return restoreHeatLabel(predicted[0][0]);
        } catch (XGBoostError ex) {
            log.warn("XGBoost inference failed, fallback to baseline, error={}", ex.getMessage());
            return forecastWindowSalesBaseline(features[0], features[1], features[2], features[3], (int) features[17]);
        }
    }

    private float[] toHeatFeatures(DishTimeseriesFeatureSnapshot snapshot, LocalDate targetDate) {
        float sales1d = snapshot.getSalesQty1d() == null ? 0F : snapshot.getSalesQty1d();
        float sales7d = snapshot.getSalesQty7d() == null ? 0F : snapshot.getSalesQty7d();
        float sales30d = snapshot.getSalesQty30d() == null ? 0F : snapshot.getSalesQty30d();
        float decaySales30d = snapshot.getDecaySales30d() == null ? 0F : snapshot.getDecaySales30d().floatValue();
        float avgSales7d = sales7d / 7F;
        float avgSales30d = sales30d / 30F;
        float salesMomentum = avgSales7d - avgSales30d;
        float[] weatherFeatures = encodeWeatherFeatures(snapshot.getWeatherCode());
        float promoFlag = snapshot.getIsPromo() == null ? 0F : snapshot.getIsPromo();
        float longTailFlag = isLongTailSnapshot(snapshot) ? 1F : 0F;
        return new float[]{
                sales1d,
                sales7d,
                sales30d,
                decaySales30d,
                avgSales7d,
                avgSales30d,
                salesMomentum,
                snapshot.getPrice() == null ? 0F : snapshot.getPrice().floatValue(),
                snapshot.getIsHoliday() == null ? 0F : snapshot.getIsHoliday(),
                promoFlag,
                promoFlag * Math.max(avgSales7d, 1F),
                weatherFeatures[0],
                weatherFeatures[1],
                weatherFeatures[2],
                weatherFeatures[3],
                weatherFeatures[4],
                targetDate == null ? 0F : targetDate.getDayOfWeek().getValue(),
                isWeekend(targetDate) ? 1F : 0F,
                Math.max(1, analysisProperties.getPredictionWindowDays()),
                snapshot.getCategoryId() == null ? 0F : snapshot.getCategoryId().floatValue(),
                longTailFlag
        };
    }

    private float[] encodeWeatherFeatures(String weatherCode) {
        String normalizedWeatherCode = normalizeWeatherCode(weatherCode);
        return switch (normalizedWeatherCode) {
            case "CLOUDY", "OVERCAST", "FOG" -> new float[]{0F, 1F, 0F, 0F, 0.25F};
            case "RAIN", "SNOW" -> new float[]{0F, 0F, 1F, 0F, 0.65F};
            case "WIND", "THUNDER" -> new float[]{0F, 0F, 0F, 1F, 1F};
            default -> new float[]{1F, 0F, 0F, 0F, 0F};
        };
    }

    private String normalizeWeatherCode(String weatherCode) {
        if (!StringUtils.hasText(weatherCode)) {
            return DEFAULT_WEATHER_CODE;
        }
        return weatherCode.trim().toUpperCase(Locale.ROOT);
    }

    private Booster loadHeatModel() {
        Path modelPath = getHeatModelPath();
        if (!Files.exists(modelPath)) {
            return null;
        }
        try {
            return XGBoost.loadModel(modelPath.toString());
        } catch (XGBoostError ex) {
            log.warn("Load XGBoost model failed, path={}, error={}", modelPath, ex.getMessage());
            return null;
        }
    }

    private String resolveHeatModelVersion(LocalDate targetDate) {
        Path metaPath = getHeatModelMetaPath();
        if (Files.exists(metaPath)) {
            try {
                Map<String, Object> meta = objectMapper.readValue(Files.readString(metaPath), new TypeReference<Map<String, Object>>() {});
                Object modelVersion = meta.get("modelVersion");
                if (modelVersion instanceof String value && StringUtils.hasText(value)) {
                    return value;
                }
            } catch (Exception ex) {
                log.warn("Read XGBoost metadata failed, path={}, error={}", metaPath, ex.getMessage());
            }
        }
        return buildModelVersion("heat-xgb", targetDate);
    }

    private Path getHeatModelDirectory() {
        return Paths.get(analysisProperties.getXgboost().getModelDir()).normalize();
    }

    private Path getHeatModelPath() {
        return getHeatModelDirectory().resolve("dish-heat-xgboost.json");
    }

    private Path getHeatModelMetaPath() {
        return getHeatModelDirectory().resolve("dish-heat-xgboost.meta.json");
    }

    private double forecastWindowSalesBaseline(DishTimeseriesFeatureSnapshot snapshot, LocalDate targetDate) {
        return forecastWindowSalesBaseline(
                snapshot.getSalesQty1d() == null ? 0F : snapshot.getSalesQty1d(),
                snapshot.getSalesQty7d() == null ? 0F : snapshot.getSalesQty7d(),
                snapshot.getSalesQty30d() == null ? 0F : snapshot.getSalesQty30d(),
                snapshot.getDecaySales30d() == null ? 0F : snapshot.getDecaySales30d().floatValue(),
                isWeekend(targetDate) ? 1 : 0
        );
    }

    private double forecastWindowSalesBaseline(float sales1d, float sales7dValue, float sales30dValue, float decay30d, int weekendFlag) {
        double sales7d = sales7dValue / 7D;
        double sales30d = sales30dValue / 30D;
        double decay = decay30d / 30D;
        double weekendBoost = weekendFlag == 1 ? 1.10D : 1D;
        double dailyForecast = ((sales1d * 0.25D) + (sales7d * 0.45D) + (sales30d * 0.20D) + (decay * 0.10D)) * weekendBoost;
        return Math.max(dailyForecast * Math.max(1, analysisProperties.getPredictionWindowDays()), 0D);
    }

    private double[][] zScore(double[][] values) {
        int rowCount = values.length;
        int colCount = values[0].length;
        double[][] normalized = new double[rowCount][colCount];
        double[] means = new double[colCount];
        double[] stds = new double[colCount];

        for (int col = 0; col < colCount; col++) {
            double sum = 0D;
            for (double[] value : values) {
                sum += value[col];
            }
            means[col] = sum / rowCount;

            double varianceSum = 0D;
            for (double[] value : values) {
                varianceSum += Math.pow(value[col] - means[col], 2);
            }
            stds[col] = Math.sqrt(varianceSum / rowCount);
            if (stds[col] == 0D) {
                stds[col] = 1D;
            }
        }

        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col < colCount; col++) {
                normalized[row][col] = (values[row][col] - means[col]) / stds[col];
            }
        }
        return normalized;
    }

    private List<double[]> initCentroids(double[][] values, int k) {
        List<double[]> centroids = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            centroids.add(values[i % values.length].clone());
        }
        return centroids;
    }

    private boolean assignClusters(double[][] values, List<double[]> centroids, int[] assignments) {
        boolean changed = false;
        for (int i = 0; i < values.length; i++) {
            double minDistance = Double.MAX_VALUE;
            int bestCluster = 0;
            for (int clusterId = 0; clusterId < centroids.size(); clusterId++) {
                double distance = euclideanDistance(values[i], centroids.get(clusterId));
                if (distance < minDistance) {
                    minDistance = distance;
                    bestCluster = clusterId;
                }
            }
            if (assignments[i] != bestCluster) {
                assignments[i] = bestCluster;
                changed = true;
            }
        }
        return changed;
    }

    private void recomputeCentroids(double[][] values, List<double[]> centroids, int[] assignments, int k) {
        int dimensions = values[0].length;
        double[][] sums = new double[k][dimensions];
        int[] counts = new int[k];
        for (int i = 0; i < values.length; i++) {
            int clusterId = assignments[i];
            counts[clusterId]++;
            for (int col = 0; col < dimensions; col++) {
                sums[clusterId][col] += values[i][col];
            }
        }

        for (int clusterId = 0; clusterId < k; clusterId++) {
            if (counts[clusterId] == 0) {
                centroids.set(clusterId, values[ThreadLocalRandom.current().nextInt(values.length)].clone());
                continue;
            }
            double[] centroid = new double[dimensions];
            for (int col = 0; col < dimensions; col++) {
                centroid[col] = sums[clusterId][col] / counts[clusterId];
            }
            centroids.set(clusterId, centroid);
        }
    }

    private double silhouetteScore(double[][] values, int[] assignments, int k) {
        if (values.length <= 1 || k <= 1) {
            return 0D;
        }
        double totalScore = 0D;
        for (int i = 0; i < values.length; i++) {
            int ownCluster = assignments[i];
            double a = averageDistance(values, assignments, i, ownCluster);
            double b = Double.MAX_VALUE;
            for (int clusterId = 0; clusterId < k; clusterId++) {
                if (clusterId == ownCluster) {
                    continue;
                }
                b = Math.min(b, averageDistance(values, assignments, i, clusterId));
            }
            if (b == Double.MAX_VALUE) {
                totalScore += 0D;
                continue;
            }
            double max = Math.max(a, b);
            totalScore += max == 0D ? 0D : (b - a) / max;
        }
        return totalScore / values.length;
    }

    private double averageDistance(double[][] values, int[] assignments, int index, int clusterId) {
        double sum = 0D;
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (i == index || assignments[i] != clusterId) {
                continue;
            }
            sum += euclideanDistance(values[index], values[i]);
            count++;
        }
        return count == 0 ? 0D : sum / count;
    }

    private double euclideanDistance(double[] left, double[] right) {
        double sum = 0D;
        for (int i = 0; i < left.length; i++) {
            sum += Math.pow(left[i] - right[i], 2);
        }
        return Math.sqrt(sum);
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
            log.warn("Parse Long list failed, error={}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Serialize JSON failed, error={}", ex.getMessage());
            return "[]";
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 100);
    }

    private LocalDate normalizeDate(LocalDate date) {
        return date == null ? LocalDate.now(APP_ZONE) : date;
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

    private enum MealPeriod {
        /** LUNCH enum item. */
        LUNCH,
        /** DINNER enum item. */
        DINNER,
        NIGHT
    }

    private enum PriceBand {
        /** LOW enum item. */
        LOW,
        /** MID enum item. */
        MID,
        HIGH
    }

    private record OrderContext(Orders order, OrderDetail detail, Dish dish) {}

    private record AnalysisWindowData(List<OrderContext> contexts, Map<Long, Orders> orderMap) {}

    private record KMeansResult(List<Integer> assignments, List<Double> confidences, double silhouette) {}

    private record HeatTrainingSample(LocalDate snapshotDate, Long dishId, float[] features, float label, float sampleWeight) {}

    private record HeatDataset(List<HeatTrainingSample> samples) {}

    private record HeatDatasetSplit(List<HeatTrainingSample> trainSamples, List<HeatTrainingSample> evalSamples) {}

    private record ClusterCachePayload(Integer clusterId, List<Long> topDishIds) {}

    private final class UserFeatureAggregation {
/**
 * HashSet<>
 * @return 
 */

        private final Set<Long> orderIds = new HashSet<>();
/**
 * HashMap<>
 * @return 
 */
        private final Map<Long, BigDecimal> orderAmountMap = new HashMap<>();
/**
 * HashMap<>
 * @return 
 */
        private final Map<MealPeriod, Integer> mealOrderCountMap = new HashMap<>();
/**
 * HashMap<>
 * @return 
 */
        private final Map<PriceBand, Integer> priceCountMap = new HashMap<>();
/**
 * HashMap<>
 * @return 
 */
        private final Map<Long, Integer> categoryCountMap = new HashMap<>();
/**
 * HashMap<>
 * @return 
 */
        private final Map<String, Integer> flavorCountMap = new HashMap<>();
        private LocalDateTime latestOrderTime;

        private void consume(OrderContext context, LocalDate snapshotDate, List<DishFlavor> flavorDefinitions) {
            Orders order = context.order();
            OrderDetail detail = context.detail();
            Dish dish = context.dish();
            if (order.getId() != null && orderIds.add(order.getId())) {
                orderAmountMap.put(order.getId(), order.getAmount() == null ? BigDecimal.ZERO : order.getAmount());
                mealOrderCountMap.merge(resolveMealPeriod(order.getOrderTime()), 1, Integer::sum);
                latestOrderTime = latestOrderTime == null || toLocalDateTime(order.getOrderTime()).isAfter(latestOrderTime)
                        ? toLocalDateTime(order.getOrderTime()) : latestOrderTime;
            }

            int quantity = quantity(detail);
            priceCountMap.merge(resolvePriceBand(dish.getPrice()), quantity, Integer::sum);
            if (dish.getCategoryId() != null) {
                categoryCountMap.merge(dish.getCategoryId(), quantity, Integer::sum);
            }

            List<String> tokens = tokenizeFlavors(detail.getDishFlavor());
            if (tokens.isEmpty()) {
                for (DishFlavor flavor : flavorDefinitions) {
                    tokens.addAll(tokenizeFlavors(flavor.getValue()));
                }
            }
            for (String token : tokens) {
                flavorCountMap.merge(token, quantity, Integer::sum);
            }
        }

        private int recencyDays(LocalDate snapshotDate) {
            if (latestOrderTime == null) {
                return 9999;
            }
            return Math.max(0, (int) ChronoUnit.DAYS.between(latestOrderTime.toLocalDate(), snapshotDate));
        }

        private int orderCount() {
            return orderIds.size();
        }

        private BigDecimal monetary() {
            return orderAmountMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal mealRatio(MealPeriod mealPeriod) {
            int total = orderCount();
            if (total == 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(mealOrderCountMap.getOrDefault(mealPeriod, 0))
                    .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
        }

        private BigDecimal priceRatio(PriceBand priceBand) {
            int total = priceCountMap.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(priceCountMap.getOrDefault(priceBand, 0))
                    .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
        }

        private Long topCategory(int index) {
            List<Long> categories = categoryCountMap.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            return categories.size() > index ? categories.get(index) : null;
        }

        private Map<String, BigDecimal> flavorVector() {
            int total = flavorCountMap.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) {
                return Collections.emptyMap();
            }
            Map<String, BigDecimal> vector = new LinkedHashMap<>();
            flavorCountMap.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(entry -> vector.put(
                            entry.getKey(),
                            BigDecimal.valueOf(entry.getValue()).divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
                    ));
            return vector;
        }
    }

    private final class DishFeatureAggregation {

        private int salesQty1d;
        private int salesQty7d;
        private int salesQty30d;
        private double decaySales30d;

        private void consume(OrderContext context, LocalDate snapshotDate, Double lambda) {
            LocalDate orderDate = toLocalDateTime(context.order().getOrderTime()).toLocalDate();
            int quantity = quantity(context.detail());
            long daysAgo = ChronoUnit.DAYS.between(orderDate, snapshotDate);
            if (daysAgo <= 0 || daysAgo > 30) {
                return;
            }
            salesQty30d += quantity;
            if (daysAgo <= 7) {
                salesQty7d += quantity;
            }
            if (daysAgo == 1) {
                salesQty1d += quantity;
            }
            decaySales30d += quantity * Math.exp(-lambda * daysAgo);
        }

        private int salesQty1d() {
            return salesQty1d;
        }

        private int salesQty7d() {
            return salesQty7d;
        }

        private int salesQty30d() {
            return salesQty30d;
        }

        private BigDecimal decaySales30d() {
            return BigDecimal.valueOf(decaySales30d);
        }
    }

    private MealPeriod resolveMealPeriod(Date orderTime) {
        LocalTime localTime = toLocalDateTime(orderTime).toLocalTime();
        if (!localTime.isBefore(LocalTime.of(10, 0)) && localTime.isBefore(LocalTime.of(15, 0))) {
            return MealPeriod.LUNCH;
        }
        if (!localTime.isBefore(LocalTime.of(17, 0)) && localTime.isBefore(LocalTime.of(22, 0))) {
            return MealPeriod.DINNER;
        }
        return MealPeriod.NIGHT;
    }

    private PriceBand resolvePriceBand(BigDecimal price) {
        BigDecimal normalizedPrice = price == null ? BigDecimal.ZERO : price;
        BigDecimal lowMax = BigDecimal.valueOf(analysisProperties.getColdStart().getPriceBand().getLowMax());
        BigDecimal midMax = BigDecimal.valueOf(analysisProperties.getColdStart().getPriceBand().getMidMax());
        if (normalizedPrice.compareTo(lowMax) <= 0) {
            return PriceBand.LOW;
        }
        if (normalizedPrice.compareTo(midMax) <= 0) {
            return PriceBand.MID;
        }
        return PriceBand.HIGH;
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
