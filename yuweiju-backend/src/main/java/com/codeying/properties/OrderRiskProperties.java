package com.codeying.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Order risk model properties.
 *
 * @author Endercloud
 */
@Data
@Validated
@ConfigurationProperties(prefix = "order-risk")
public class OrderRiskProperties {

    @NotBlank(message = "order-risk.feature-version must not be blank")
    private String featureVersion = "v1";

    @NotBlank(message = "order-risk.model-dir must not be blank")
    private String modelDir = "./runtime/order-risk-models";

    @Valid
    @NotNull
    private Thresholds thresholds = new Thresholds();

    @Valid
    @NotNull
    private Task task = new Task();

    @Valid
    @NotNull
    private Training training = new Training();

    @Valid
    @NotNull
    private Scoring scoring = new Scoring();

    @Data
    public static class Thresholds {
        @Min(value = 0, message = "order-risk.thresholds.low must be >= 0")
        @Max(value = 100, message = "order-risk.thresholds.low must be <= 100")
        private Integer low = 12;

        @Min(value = 0, message = "order-risk.thresholds.medium must be >= 0")
        @Max(value = 100, message = "order-risk.thresholds.medium must be <= 100")
        private Integer medium = 16;
    }

    @Data
    public static class Task {
        @NotNull(message = "order-risk.task.scoring-enabled must not be null")
        private Boolean scoringEnabled = true;

        @NotBlank(message = "order-risk.task.training-cron must not be blank")
        private String trainingCron = "0 30 3 ? * MON";

        @Min(value = 1, message = "order-risk.task.backfill-days must be >= 1")
        @Max(value = 365, message = "order-risk.task.backfill-days must be <= 365")
        private Integer backfillDays = 120;

        @Min(value = 1, message = "order-risk.task.backfill-max-orders must be >= 1")
        @Max(value = 20000, message = "order-risk.task.backfill-max-orders must be <= 20000")
        private Integer backfillMaxOrders = 5000;
    }

    @Data
    public static class Training {
        @Min(value = 1, message = "order-risk.training.min-anomaly-labels must be >= 1")
        private Integer minAnomalyLabels = 50;

        @NotNull(message = "order-risk.training.reject-if-metric-regression must not be null")
        private Boolean rejectIfMetricRegression = true;

        @Valid
        @NotNull
        private RejectThresholds rejectThresholds = new RejectThresholds();
    }

    @Data
    public static class RejectThresholds {
        @DecimalMin(value = "0.0", message = "order-risk.training.reject-thresholds.pr-auc-delta must be >= 0")
        private Double prAucDelta = 0.02;

        @DecimalMin(value = "0.0", message = "order-risk.training.reject-thresholds.recall-delta must be >= 0")
        private Double recallDelta = 0.05;
    }

    @Data
    public static class Scoring {
        @DecimalMin(value = "0.0", message = "order-risk.scoring.lr-weight must be >= 0")
        @DecimalMax(value = "1.0", message = "order-risk.scoring.lr-weight must be <= 1")
        private Double lrWeight = 0.4;

        @DecimalMin(value = "0.0", message = "order-risk.scoring.rf-weight must be >= 0")
        @DecimalMax(value = "1.0", message = "order-risk.scoring.rf-weight must be <= 1")
        private Double rfWeight = 0.6;
    }
}
