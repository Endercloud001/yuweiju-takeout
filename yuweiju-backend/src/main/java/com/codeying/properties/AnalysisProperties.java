package com.codeying.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 热度分析与数据挖掘配置。
 *
 * @author Endercloud
 */
@Data
@Validated
@ConfigurationProperties(prefix = "analysis")
public class AnalysisProperties {

    @NotBlank(message = "analysis.feature-version 不能为空")
    private String featureVersion;

    @NotBlank(message = "analysis.model-version-prefix 不能为空")
    private String modelVersionPrefix;

    @Min(value = 1, message = "analysis.cluster-count 不能小于 1")
    private Integer clusterCount;

    @Min(value = 1, message = "analysis.prediction-window-days 不能小于 1")
    private Integer predictionWindowDays;

    @Min(value = 7, message = "analysis.training-lookback-days 不能小于 7")
    private Integer trainingLookbackDays;

    @Min(value = 1, message = "analysis.backfill-days 不能小于 1")
    private Integer backfillDays;

    @DecimalMin(value = "0.0", message = "analysis.decay-lambda 不能小于 0")
    private Double decayLambda;

    @Valid
    @NotNull
    private TaskProperties task = new TaskProperties();

    @Valid
    @NotNull
    private ColdStartProperties coldStart = new ColdStartProperties();

    @Valid
    @NotNull
    private RedisProperties redis = new RedisProperties();

    @Valid
    @NotNull
    private ValidationProperties validation = new ValidationProperties();

    @Valid
    @NotNull
    private XgboostProperties xgboost = new XgboostProperties();

    @Valid
    @NotNull
    private ObservationProperties observation = new ObservationProperties();

    @Valid
    @NotNull
    private ExternalProperties external = new ExternalProperties();

    /**
     * 定时任务配置。
     */
    @Data
    public static class TaskProperties {

        @NotNull(message = "analysis.task.enabled 不能为空")
        private Boolean enabled;

        @NotBlank(message = "analysis.task.snapshot-cron 不能为空")
        private String snapshotCron;

        @NotBlank(message = "analysis.task.prediction-cron 不能为空")
        private String predictionCron;

        @NotBlank(message = "analysis.task.training-cron 不能为空")
        private String trainingCron;
    }

    /**
     * 冷启动策略配置。
     */
    @Data
    public static class ColdStartProperties {

        @Min(value = 1, message = "analysis.cold-start.top-n 不能小于 1")
        private Integer topN;

        @Min(value = 1, message = "analysis.cold-start.bayesian-smoothing-m 不能小于 1")
        private Integer bayesianSmoothingM;

        @Valid
        @NotNull
        private PriceBandProperties priceBand = new PriceBandProperties();
    }

    /**
     * 价格带配置。
     */
    @Data
    public static class PriceBandProperties {

        @Min(value = 0, message = "analysis.cold-start.price-band.low-max 不能小于 0")
        private Integer lowMax;

        @Min(value = 0, message = "analysis.cold-start.price-band.mid-max 不能小于 0")
        private Integer midMax;
    }

    /**
     * Redis 缓存配置。
     */
    @Data
    public static class RedisProperties {

        @Min(value = 1, message = "analysis.redis.heat-prediction-ttl-hours 不能小于 1")
        private Integer heatPredictionTtlHours;

        @Min(value = 1, message = "analysis.redis.cluster-user-ttl-hours 不能小于 1")
        private Integer clusterUserTtlHours;

        @Min(value = 1, message = "analysis.redis.cluster-topn-ttl-hours 不能小于 1")
        private Integer clusterTopnTtlHours;

        @Min(value = 1, message = "analysis.redis.fallback-ttl-hours 不能小于 1")
        private Integer fallbackTtlHours;
    }

    /**
     * 验收阈值配置。
     */
    @Data
    public static class ValidationProperties {

        @DecimalMin(value = "0.0", message = "analysis.validation.rmse-ratio-threshold 不能小于 0")
        private Double rmseRatioThreshold;

        @DecimalMin(value = "0.0", message = "analysis.validation.mae-ratio-threshold 不能小于 0")
        private Double maeRatioThreshold;

        @DecimalMin(value = "0.0", message = "analysis.validation.silhouette-min 不能小于 0")
        private Double silhouetteMin;

        @DecimalMin(value = "0.0", message = "analysis.validation.ctr-min 不能小于 0")
        private Double ctrMin;

        @DecimalMin(value = "0.0", message = "analysis.validation.cvr-min 不能小于 0")
        private Double cvrMin;
    }

    /**
     * XGBoost 参数配置。
     */
    @Data
    public static class XgboostProperties {

        @NotNull(message = "analysis.xgboost.enabled 不能为空")
        private Boolean enabled;

        @Min(value = 1, message = "analysis.xgboost.rounds 不能小于 1")
        private Integer rounds;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.eta 不能小于 0")
        private Double eta;

        @Min(value = 1, message = "analysis.xgboost.max-depth 不能小于 1")
        private Integer maxDepth;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.subsample 不能小于 0")
        private Double subsample;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.colsample-bytree 不能小于 0")
        private Double colsampleBytree;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.min-child-weight 不能小于 0")
        private Double minChildWeight;

        @Min(value = 1, message = "analysis.xgboost.eval-holdout-days 不能小于 1")
        private Integer evalHoldoutDays;

        @Min(value = 1, message = "analysis.xgboost.long-tail-sales-threshold 不能小于 1")
        private Integer longTailSalesThreshold;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.zero-label-keep-rate 不能小于 0")
        private Double zeroLabelKeepRate;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.active-sample-weight 不能小于 0")
        private Double activeSampleWeight;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.promo-sample-weight 不能小于 0")
        private Double promoSampleWeight;

        @DecimalMin(value = "0.0", message = "analysis.xgboost.severe-weather-sample-weight 不能小于 0")
        private Double severeWeatherSampleWeight;

        @NotNull(message = "analysis.xgboost.enable-log-label 不能为空")
        private Boolean enableLogLabel;

        @NotBlank(message = "analysis.xgboost.model-dir 不能为空")
        private String modelDir;
    }

    /**
     * 线上观察窗口配置。
     */
    @Data
    public static class ObservationProperties {

        @Min(value = 1, message = "analysis.observation.rolling-7d-days 不能小于 1")
        private Integer rolling7dDays = 7;

        @Min(value = 1, message = "analysis.observation.rolling-30d-days 不能小于 1")
        private Integer rolling30dDays = 30;
    }

    /**
     * 外部特征接入配置。
     */
    @Data
    public static class ExternalProperties {

        @NotBlank(message = "analysis.external.weather-location 不能为空")
        private String weatherLocation;

        @Min(value = 1, message = "analysis.external.weather-history-days 不能小于 1")
        private Integer weatherHistoryDays = 10;

        @Min(value = 1, message = "analysis.external.weather-cache-hours 不能小于 1")
        private Integer weatherCacheHours = 12;
/**
 * java.util.ArrayList<>
 * @return 
 */

        @NotNull(message = "analysis.external.promo-periods 不能为空")
        private java.util.List<PromoPeriodProperties> promoPeriods = new java.util.ArrayList<>();
    }

    /**
     * 促销时间窗配置。
     */
    @Data
    public static class PromoPeriodProperties {

        @NotBlank(message = "analysis.external.promo-periods[].id 不能为空")
        private String id;

        @NotNull(message = "analysis.external.promo-periods[].start 不能为空")
        private java.time.LocalDate start;

        @NotNull(message = "analysis.external.promo-periods[].end 不能为空")
        private java.time.LocalDate end;

        private String name;
/**
 * java.util.ArrayList<>
 * @return 
 */

        private java.util.List<Long> dishIds = new java.util.ArrayList<>();
/**
 * java.util.ArrayList<>
 * @return 
 */

        private java.util.List<Long> categoryIds = new java.util.ArrayList<>();
    }
}
