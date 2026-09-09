package com.codeying.constant;

/**
 * Redis Key 统一管理。
 *
 * @author Endercloud
 */
public class RedisKeys {

    private RedisKeys() {
    }

    /**
     * JWT 黑名单 key。
     *
     * @param jti JWT ID
     * @return key
     */
    public static String tokenBlacklistKey(String jti) {
        return "yuweiju:jwt:blacklist:" + jti;
    }

    /**
     * 店铺营业状态 key。
     *
     * @return key
     */
    public static String shopStatusKey() {
        return "yuweiju:shop:status";
    }

    /**
     * 催单节流 key（同一用户同一订单的短期限流）。
     *
     * @param userId  用户 ID
     * @param orderId 订单 ID
     * @return key
     */
    public static String orderReminderThrottleKey(Long userId, Long orderId) {
        return "yuweiju:order:reminder:" + userId + ":" + orderId;
    }

    /**
     * AI 助手天气缓存 key。
     *
     * @param location 查询地理位置
     * @return key
     */
    public static String aiWeatherCacheKey(String location) {
        return "yuweiju:ai:weather:" + location;
    }

    /**
     * 分析任务天气特征缓存 key。
     *
     * @param location 查询位置
     * @param date 日期（yyyy-MM-dd）
     * @return key
     */
    public static String analysisWeatherFeatureKey(String location, String date) {
        return "yuweiju:analysis:weather:" + location + ":" + date;
    }

    /**
     * 菜品热度预测缓存 key。
     *
     * @param windowStart 预测窗口开始日期
     * @param dishId       菜品 ID
     * @return key
     */
    public static String dishHeatPredictionKey(String windowStart, Long dishId) {
        return "heat:pred:" + windowStart + ":" + dishId;
    }

    /**
     * 用户聚类结果缓存 key。
     *
     * @param userId 用户 ID
     * @return key
     */
    public static String userClusterKey(Long userId) {
        return "cluster:user:" + userId;
    }

    /**
     * 簇级候选菜品缓存 key。
     *
     * @param clusterId 簇 ID
     * @return key
     */
    public static String clusterTopnKey(Integer clusterId) {
        return "cluster:topn:" + clusterId;
    }

    /**
     * 冷启动兜底列表缓存 key。
     *
     * @param topN 候选数量
     * @return key
     */
    public static String heatFallbackTopKey(Integer topN) {
        return "heat:fallback:top" + topN;
    }

    public static String aiRecommendContextKey(Long userId) {
        return "yuweiju:analysis:ai:context:" + userId;
    }

    public static String aiRecommendExposureUsersKey(String date, String modelVersion) {
        return "yuweiju:analysis:ai:exposed:" + date + ":" + modelVersion;
    }

    public static String aiRecommendExposureCountKey(String date, String modelVersion) {
        return "yuweiju:analysis:ai:exposed:count:" + date + ":" + modelVersion;
    }

    public static String aiRecommendClickUsersKey(String date, String modelVersion) {
        return "yuweiju:analysis:ai:clicked:" + date + ":" + modelVersion;
    }

    public static String aiRecommendClickCountKey(String date, String modelVersion) {
        return "yuweiju:analysis:ai:clicked:count:" + date + ":" + modelVersion;
    }

    public static String aiRecommendConversionUsersKey(String date, String modelVersion) {
        return "yuweiju:analysis:ai:converted:" + date + ":" + modelVersion;
    }

    public static String aiRecommendConversionCountKey(String date, String modelVersion) {
        return "yuweiju:analysis:ai:converted:count:" + date + ":" + modelVersion;
    }

    public static String analysisOfflineValidationKey(String date) {
        return "yuweiju:analysis:offline:validation:" + date;
    }
}
