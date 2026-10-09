package com.codeying.service.impl;

import com.codeying.constant.RedisKeys;
import com.codeying.properties.AnalysisProperties;
import com.codeying.service.AnalysisObservationService;
import com.codeying.vo.common.ai_assistant.AiAssistantDishCardVO;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analysis Observation Service Impl service implementation.
 *
 * @author Endercloud
 */
@Service
public class AnalysisObservationServiceImpl implements AnalysisObservationService {
/**
 * LoggerFactory.getLogger
 * @return 
 */

    private static final Logger log = LoggerFactory.getLogger(AnalysisObservationServiceImpl.class);
/**
 * ZoneId.of
 * @return 
 */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
/**
 * Duration.ofHours
 * @return 
 */
    private static final Duration CONTEXT_TTL = Duration.ofHours(24);
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AnalysisProperties analysisProperties;

    public AnalysisObservationServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AnalysisProperties analysisProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.analysisProperties = analysisProperties;
    }

    @Override
    public void recordRecommendationExposure(Long userId, Long sessionId, Long messageId, AiAssistantSendReplyVO replyVO) {
        try {
            if (userId == null || sessionId == null || messageId == null || replyVO == null || replyVO.getDishes() == null || replyVO.getDishes().isEmpty()) {
                return;
            }
            ObservationPayload payload = readPayload(replyVO.getPayload());
            String modelVersion = normalizeModelVersion(payload.modelVersion());
            LocalDate observeDate = LocalDate.now(APP_ZONE);
            List<Long> dishIds = replyVO.getDishes().stream()
                    .map(AiAssistantDishCardVO::getDishId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
            if (dishIds.isEmpty()) {
                return;
            }

            ObservationContext context = new ObservationContext(
                    observeDate.toString(),
                    sessionId,
                    messageId,
                    payload.strategy(),
                    modelVersion,
                    payload.fallback(),
                    dishIds,
                    new ArrayList<>(),
                    new ArrayList<>()
            );
            saveContext(userId, context);
            addUserToMetricSet(RedisKeys.aiRecommendExposureUsersKey(observeDate.toString(), modelVersion), userId);
            incrementMetricCounter(RedisKeys.aiRecommendExposureCountKey(observeDate.toString(), modelVersion));
            log.info("[Analysis-Exposure] date={}, userId={}, sessionId={}, messageId={}, modelVersion={}, strategy={}, fallback={}, dishIds={}",
                    observeDate,
                    userId,
                    sessionId,
                    messageId,
                    modelVersion,
                    payload.strategy(),
                    payload.fallback(),
                    dishIds);
            logOnlineObservation(observeDate, modelVersion);
        } catch (RuntimeException ex) {
            // Optional observation must never roll back the calling business use case.
            log.warn("AI recommendation observation unavailable, operation={}, failure={}",
                    "recordRecommendationExposure", ex.getClass().getSimpleName());
        }
    }

    @Override
    public void recordRecommendationClick(Long userId, Long dishId) {
        try {
            if (userId == null || dishId == null) {
                return;
            }
            ObservationContext context = readContext(userId);
            if (context == null || context.dishIds() == null || !context.dishIds().contains(dishId)) {
                return;
            }
            Set<Long> clickedDishIds = new LinkedHashSet<>(safeList(context.clickedDishIds()));
            if (!clickedDishIds.add(dishId)) {
                return;
            }
            ObservationContext updated = new ObservationContext(
                    context.observeDate(),
                    context.sessionId(),
                    context.messageId(),
                    context.strategy(),
                    context.modelVersion(),
                    context.fallback(),
                    safeList(context.dishIds()),
                    new ArrayList<>(clickedDishIds),
                    safeList(context.convertedOrderIds())
            );
            saveContext(userId, updated);
            addUserToMetricSet(RedisKeys.aiRecommendClickUsersKey(context.observeDate(), context.modelVersion()), userId);
            incrementMetricCounter(RedisKeys.aiRecommendClickCountKey(context.observeDate(), context.modelVersion()));
            log.info("[Analysis-CTR] date={}, userId={}, modelVersion={}, strategy={}, clickedDishId={}",
                    context.observeDate(),
                    userId,
                    context.modelVersion(),
                    context.strategy(),
                    dishId);
            logOnlineObservation(LocalDate.parse(context.observeDate()), context.modelVersion());
        } catch (RuntimeException ex) {
            // Optional observation must never roll back the calling business use case.
            log.warn("AI recommendation observation unavailable, operation={}, failure={}",
                    "recordRecommendationClick", ex.getClass().getSimpleName());
        }
    }

    @Override
    public void recordRecommendationConversion(Long userId, Long orderId, List<Long> dishIds) {
        try {
            if (userId == null || orderId == null || dishIds == null || dishIds.isEmpty()) {
                return;
            }
            ObservationContext context = readContext(userId);
            if (context == null || context.clickedDishIds() == null || context.clickedDishIds().isEmpty()) {
                return;
            }
            Set<Long> clickedDishIds = new LinkedHashSet<>(safeList(context.clickedDishIds()));
            boolean matched = dishIds.stream().anyMatch(clickedDishIds::contains);
            if (!matched) {
                return;
            }
            Set<Long> convertedOrderIds = new LinkedHashSet<>(safeList(context.convertedOrderIds()));
            if (!convertedOrderIds.add(orderId)) {
                return;
            }
            ObservationContext updated = new ObservationContext(
                    context.observeDate(),
                    context.sessionId(),
                    context.messageId(),
                    context.strategy(),
                    context.modelVersion(),
                    context.fallback(),
                    safeList(context.dishIds()),
                    safeList(context.clickedDishIds()),
                    new ArrayList<>(convertedOrderIds)
            );
            saveContext(userId, updated);
            addUserToMetricSet(RedisKeys.aiRecommendConversionUsersKey(context.observeDate(), context.modelVersion()), userId);
            incrementMetricCounter(RedisKeys.aiRecommendConversionCountKey(context.observeDate(), context.modelVersion()));
            log.info("[Analysis-CVR] date={}, userId={}, orderId={}, modelVersion={}, strategy={}, matchedDishIds={}",
                    context.observeDate(),
                    userId,
                    orderId,
                    context.modelVersion(),
                    context.strategy(),
                    dishIds.stream().filter(clickedDishIds::contains).distinct().toList());
            logOnlineObservation(LocalDate.parse(context.observeDate()), context.modelVersion());
        } catch (RuntimeException ex) {
            // Optional observation must never roll back the calling business use case.
            log.warn("AI recommendation observation unavailable, operation={}, failure={}",
                    "recordRecommendationConversion", ex.getClass().getSimpleName());
        }
    }

    @Override
    public void logOnlineObservation(LocalDate observeDate, String modelVersion) {
        try {
            if (observeDate == null) {
                return;
            }
            String normalizedModelVersion = normalizeModelVersion(modelVersion);
            String date = observeDate.toString();
            RollingObservation daily = loadRollingObservation(observeDate, 1, normalizedModelVersion);

            log.info("[Analysis-Observation] date={}, modelVersion={}, exposedUsers={}, clickedUsers={}, convertedUsers={}, exposureCount={}, clickCount={}, conversionCount={}, ctrUv={}, cvrUv={}, ctrPv={}, cvrPv={}",
                    date,
                    normalizedModelVersion,
                    daily.exposedUsers(),
                    daily.clickedUsers(),
                    daily.convertedUsers(),
                    daily.exposureCount(),
                    daily.clickCount(),
                    daily.conversionCount(),
                    scale(daily.ctrUv()),
                    scale(daily.cvrUv()),
                    scale(daily.ctrPv()),
                    scale(daily.cvrPv()));
            if (daily.exposedUsers() > 0 && daily.ctrUv() < analysisProperties.getValidation().getCtrMin()) {
                log.warn("[Analysis-Observation] CTR below threshold, date={}, modelVersion={}, ctr={}, threshold={}",
                        date,
                        normalizedModelVersion,
                        scale(daily.ctrUv()),
                        analysisProperties.getValidation().getCtrMin());
            }
            if (daily.clickedUsers() > 0 && daily.cvrUv() < analysisProperties.getValidation().getCvrMin()) {
                log.warn("[Analysis-Observation] CVR below threshold, date={}, modelVersion={}, cvr={}, threshold={}",
                        date,
                        normalizedModelVersion,
                        scale(daily.cvrUv()),
                        analysisProperties.getValidation().getCvrMin());
            }
            logRollingObservation(observeDate, normalizedModelVersion);
        } catch (RuntimeException ex) {
            // Optional observation must never roll back the calling business use case.
            log.warn("AI recommendation observation unavailable, operation={}, failure={}",
                    "logOnlineObservation", ex.getClass().getSimpleName());
        }
    }

    @Override
    public Map<String, Object> getOnlineObservationSummary(LocalDate observeDate, String modelVersion) {
        LocalDate targetDate = observeDate == null ? LocalDate.now(APP_ZONE) : observeDate;
        String normalizedModelVersion = normalizeModelVersion(modelVersion);
        int rolling7dDays = Math.max(1, analysisProperties.getObservation().getRolling7dDays());
        int rolling30dDays = Math.max(1, analysisProperties.getObservation().getRolling30dDays());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("observeDate", targetDate.toString());
        result.put("modelVersion", normalizedModelVersion);
        result.put("daily", buildRollingMap(loadRollingObservation(targetDate, 1, normalizedModelVersion)));
        result.put("rolling7d", buildRollingMap(loadRollingObservation(targetDate, rolling7dDays, normalizedModelVersion)));
        result.put("rolling30d", buildRollingMap(loadRollingObservation(targetDate, rolling30dDays, normalizedModelVersion)));
        result.put("ctrThreshold", analysisProperties.getValidation().getCtrMin());
        result.put("cvrThreshold", analysisProperties.getValidation().getCvrMin());
        return result;
    }

    private ObservationPayload readPayload(Object rawPayload) {
        if (!(rawPayload instanceof Map<?, ?> payloadMap)) {
            return new ObservationPayload("unknown", "unknown", true);
        }
        String strategy = payloadMap.get("analysisStrategy") instanceof String value ? value : "unknown";
        String modelVersion = payloadMap.get("analysisModelVersion") instanceof String value ? value : "unknown";
        boolean fallback = payloadMap.get("analysisFallback") instanceof Boolean value && value;
        return new ObservationPayload(strategy, modelVersion, fallback);
    }

    private ObservationContext readContext(Long userId) {
        String raw = stringRedisTemplate.opsForValue().get(RedisKeys.aiRecommendContextKey(userId));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, ObservationContext.class);
        } catch (Exception ex) {
            log.warn("Recommendation context unavailable, userId={}, failure={}", userId, ex.getClass().getSimpleName());
            return null;
        }
    }

    private void saveContext(Long userId, ObservationContext context) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeys.aiRecommendContextKey(userId),
                    objectMapper.writeValueAsString(context),
                    CONTEXT_TTL
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Recommendation context write failed", ex);
        }
    }

    private void logRollingObservation(LocalDate observeDate, String modelVersion) {
        if (observeDate == null) {
            return;
        }
        logRollingWindow("7d", observeDate, Math.max(1, analysisProperties.getObservation().getRolling7dDays()), modelVersion);
        logRollingWindow("30d", observeDate, Math.max(1, analysisProperties.getObservation().getRolling30dDays()), modelVersion);
    }

    private void logRollingWindow(String windowName, LocalDate observeDate, int windowDays, String modelVersion) {
        RollingObservation rolling = loadRollingObservation(observeDate, windowDays, modelVersion);
        log.info("[Analysis-Observation-Rolling] window={}, endDate={}, modelVersion={}, exposedUsers={}, clickedUsers={}, convertedUsers={}, exposureCount={}, clickCount={}, conversionCount={}, ctrUv={}, cvrUv={}, ctrPv={}, cvrPv={}",
                windowName,
                observeDate,
                modelVersion,
                rolling.exposedUsers(),
                rolling.clickedUsers(),
                rolling.convertedUsers(),
                rolling.exposureCount(),
                rolling.clickCount(),
                rolling.conversionCount(),
                scale(rolling.ctrUv()),
                scale(rolling.cvrUv()),
                scale(rolling.ctrPv()),
                scale(rolling.cvrPv()));
    }

    private Map<String, Object> buildRollingMap(RollingObservation rolling) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exposedUsers", rolling.exposedUsers());
        result.put("clickedUsers", rolling.clickedUsers());
        result.put("convertedUsers", rolling.convertedUsers());
        result.put("exposureCount", rolling.exposureCount());
        result.put("clickCount", rolling.clickCount());
        result.put("conversionCount", rolling.conversionCount());
        result.put("ctrUv", scale(rolling.ctrUv()));
        result.put("cvrUv", scale(rolling.cvrUv()));
        result.put("ctrPv", scale(rolling.ctrPv()));
        result.put("cvrPv", scale(rolling.cvrPv()));
        return result;
    }

    private RollingObservation loadRollingObservation(LocalDate observeDate, int windowDays, String modelVersion) {
        Set<String> exposedUsers = new HashSet<>();
        Set<String> clickedUsers = new HashSet<>();
        Set<String> convertedUsers = new HashSet<>();
        long exposureCount = 0L;
        long clickCount = 0L;
        long conversionCount = 0L;
        for (int offset = 0; offset < windowDays; offset++) {
            String date = observeDate.minusDays(offset).toString();
            exposedUsers.addAll(members(RedisKeys.aiRecommendExposureUsersKey(date, modelVersion)));
            clickedUsers.addAll(members(RedisKeys.aiRecommendClickUsersKey(date, modelVersion)));
            convertedUsers.addAll(members(RedisKeys.aiRecommendConversionUsersKey(date, modelVersion)));
            exposureCount += counter(RedisKeys.aiRecommendExposureCountKey(date, modelVersion));
            clickCount += counter(RedisKeys.aiRecommendClickCountKey(date, modelVersion));
            conversionCount += counter(RedisKeys.aiRecommendConversionCountKey(date, modelVersion));
        }
        double ctrUv = exposedUsers.isEmpty() ? 0D : clickedUsers.size() / (double) exposedUsers.size();
        double cvrUv = clickedUsers.isEmpty() ? 0D : convertedUsers.size() / (double) clickedUsers.size();
        double ctrPv = exposureCount <= 0 ? 0D : clickCount / (double) exposureCount;
        double cvrPv = clickCount <= 0 ? 0D : conversionCount / (double) clickCount;
        return new RollingObservation(
                exposedUsers.size(),
                clickedUsers.size(),
                convertedUsers.size(),
                exposureCount,
                clickCount,
                conversionCount,
                ctrUv,
                cvrUv,
                ctrPv,
                cvrPv
        );
    }

    private void addUserToMetricSet(String key, Long userId) {
        stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));
        if (!Boolean.TRUE.equals(stringRedisTemplate.expire(key, metricTtl()))) {
            throw new IllegalStateException("Recommendation metric expiry failed");
        }
    }

    private Set<String> members(String key) {
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    private void incrementMetricCounter(String key) {
        stringRedisTemplate.opsForValue().increment(key);
        if (!Boolean.TRUE.equals(stringRedisTemplate.expire(key, metricTtl()))) {
            throw new IllegalStateException("Recommendation metric expiry failed");
        }
    }

    private Duration metricTtl() {
        int maxWindowDays = Math.max(
                Math.max(1, analysisProperties.getObservation().getRolling7dDays()),
                Math.max(1, analysisProperties.getObservation().getRolling30dDays())
        );
        return Duration.ofDays(maxWindowDays + 7L);
    }

    private Long counter(String key) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Long size(String key) {
        Long result = stringRedisTemplate.opsForSet().size(key);
        return result == null ? 0L : result;
    }

    private String normalizeModelVersion(String modelVersion) {
        return StringUtils.hasText(modelVersion) ? modelVersion.trim() : "unknown";
    }

    private List<Long> safeList(List<Long> values) {
        return values == null ? List.of() : values.stream().filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
    }

    private String scale(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private record ObservationPayload(String strategy, String modelVersion, boolean fallback) {
    }

    private record ObservationContext(
            String observeDate,
            Long sessionId,
            Long messageId,
            String strategy,
            String modelVersion,
            boolean fallback,
            List<Long> dishIds,
            List<Long> clickedDishIds,
            List<Long> convertedOrderIds
    ) {
    }

    private record RollingObservation(
            int exposedUsers,
            int clickedUsers,
            int convertedUsers,
            long exposureCount,
            long clickCount,
            long conversionCount,
            double ctrUv,
            double cvrUv,
            double ctrPv,
            double cvrPv
    ) {
    }
}
