package com.codeying.risk;

import java.util.*;

/** Pure feature standardization, class balancing and holdout calculations; no IO or business orchestration. */
public final class RiskCalculations {
    private RiskCalculations() {}
    public static final List<String> FEATURE_NAMES = List.of(
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
    public static Map<String, double[]> buildStandardizer(double[][] raw) {
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

    public static double[][] applyStandardizer(double[][] raw, Map<String, double[]> standardizer) {
        double[][] standardized = new double[raw.length][FEATURE_NAMES.size()];
        for (int i = 0; i < raw.length; i++) {
            standardized[i] = standardize(raw[i], standardizer);
        }
        return standardized;
    }

    public static double[] standardize(double[] raw, Map<String, double[]> standardizer) {
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

    public static int[] buildClassWeight(int[] labels) {
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

    public static double computePrAuc(int[] labels, double[] probabilities) {
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

    public static double computeRecallAtTopPercent(int[] labels, double[] probabilities, double topPercent) {
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

    public static double computeFalsePositiveRate(int[] labels, double[] probabilities, double threshold) {
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

    public static List<Integer> sortIndexesByProbability(double[] probabilities) {
        List<Integer> indexes = new ArrayList<>(probabilities.length);
        for (int i = 0; i < probabilities.length; i++) {
            indexes.add(i);
        }
        indexes.sort((a, b) -> Double.compare(probabilities[b], probabilities[a]));
        return indexes;
    }

}
