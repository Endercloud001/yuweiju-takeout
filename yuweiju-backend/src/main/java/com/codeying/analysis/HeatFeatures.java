package com.codeying.analysis;

import com.codeying.entity.DishTimeseriesFeatureSnapshot;
import com.codeying.properties.AnalysisProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** Original feature encoding, weighting, label transformation and baseline equations; no IO. */
@Component
public class HeatFeatures {
    private static final String DEFAULT_WEATHER_CODE = "CLEAR";
    private final AnalysisProperties analysisProperties;
    public HeatFeatures(AnalysisProperties analysisProperties) { this.analysisProperties = analysisProperties; }
    public boolean shouldKeepHeatSample(DishTimeseriesFeatureSnapshot snapshot, LocalDate sampleDate, int label) {
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

    public float resolveHeatSampleWeight(DishTimeseriesFeatureSnapshot snapshot, int label) {
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

    public boolean isLongTailSnapshot(DishTimeseriesFeatureSnapshot snapshot) {
        return snapshot == null || valueOrZero(snapshot.getSalesQty30d()) <= analysisProperties.getXgboost().getLongTailSalesThreshold();
    }

    public boolean isNeutralWeather(String weatherCode) {
        String normalizedWeatherCode = normalizeWeatherCode(weatherCode);
        return Objects.equals(DEFAULT_WEATHER_CODE, normalizedWeatherCode) || Objects.equals("CLEAR", normalizedWeatherCode);
    }

    public boolean isSevereWeather(String weatherCode) {
        String normalizedWeatherCode = normalizeWeatherCode(weatherCode);
        return Objects.equals("RAIN", normalizedWeatherCode)
                || Objects.equals("SNOW", normalizedWeatherCode)
                || Objects.equals("THUNDER", normalizedWeatherCode)
                || Objects.equals("WIND", normalizedWeatherCode);
    }

    public float transformHeatLabel(float rawLabel) {
        if (!Boolean.TRUE.equals(analysisProperties.getXgboost().getEnableLogLabel())) {
            return Math.max(rawLabel, 0F);
        }
        return (float) Math.log1p(Math.max(rawLabel, 0F));
    }

    public double restoreHeatLabel(float predictedLabel) {
        double safeValue = Math.max(predictedLabel, 0F);
        if (!Boolean.TRUE.equals(analysisProperties.getXgboost().getEnableLogLabel())) {
            return safeValue;
        }
        return Math.max(Math.expm1(safeValue), 0D);
    }

    public float[] toHeatFeatures(DishTimeseriesFeatureSnapshot snapshot, LocalDate targetDate) {
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

    public float[] encodeWeatherFeatures(String weatherCode) {
        String normalizedWeatherCode = normalizeWeatherCode(weatherCode);
        return switch (normalizedWeatherCode) {
            case "CLOUDY", "OVERCAST", "FOG" -> new float[]{0F, 1F, 0F, 0F, 0.25F};
            case "RAIN", "SNOW" -> new float[]{0F, 0F, 1F, 0F, 0.65F};
            case "WIND", "THUNDER" -> new float[]{0F, 0F, 0F, 1F, 1F};
            default -> new float[]{1F, 0F, 0F, 0F, 0F};
        };
    }

    public String normalizeWeatherCode(String weatherCode) {
        if (!StringUtils.hasText(weatherCode)) {
            return DEFAULT_WEATHER_CODE;
        }
        return weatherCode.trim().toUpperCase(Locale.ROOT);
    }

    public double forecastWindowSalesBaseline(DishTimeseriesFeatureSnapshot snapshot, LocalDate targetDate) {
        return forecastWindowSalesBaseline(
                snapshot.getSalesQty1d() == null ? 0F : snapshot.getSalesQty1d(),
                snapshot.getSalesQty7d() == null ? 0F : snapshot.getSalesQty7d(),
                snapshot.getSalesQty30d() == null ? 0F : snapshot.getSalesQty30d(),
                snapshot.getDecaySales30d() == null ? 0F : snapshot.getDecaySales30d().floatValue(),
                isWeekend(targetDate) ? 1 : 0
        );
    }

    public double forecastWindowSalesBaseline(float sales1d, float sales7dValue, float sales30dValue, float decay30d, int weekendFlag) {
        double sales7d = sales7dValue / 7D;
        double sales30d = sales30dValue / 30D;
        double decay = decay30d / 30D;
        double weekendBoost = weekendFlag == 1 ? 1.10D : 1D;
        double dailyForecast = ((sales1d * 0.25D) + (sales7d * 0.45D) + (sales30d * 0.20D) + (decay * 0.10D)) * weekendBoost;
        return Math.max(dailyForecast * Math.max(1, analysisProperties.getPredictionWindowDays()), 0D);
    }

    private static double valueOrZero(Integer value) { return value == null ? 0D : value.doubleValue(); }
    private static boolean isWeekend(LocalDate date) { return date != null && date.getDayOfWeek().getValue() >= 6; }
}
