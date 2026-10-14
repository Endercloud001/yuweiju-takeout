package com.codeying.analysis;

import com.codeying.entity.UserBehaviorFeatureSnapshot;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/** Original clustering math, unchanged; no database/cache/model-file responsibility. */
public final class UserClustering {
    private UserClustering() {}
    public record Result(List<Integer> assignments, List<Double> confidences, double silhouette) {}
    private static double value(Integer value) { return value == null ? 0D : value.doubleValue(); }
    private static double number(BigDecimal value) { return value == null ? 0D : value.doubleValue(); }
    public static Result cluster(List<UserBehaviorFeatureSnapshot> snapshots, int clusterCount) {
        if (snapshots.size() == 1) {
            return new Result(List.of(0), List.of(1D), 0D);
        }

        double[][] features = new double[snapshots.size()][];
        for (int i = 0; i < snapshots.size(); i++) {
            UserBehaviorFeatureSnapshot snapshot = snapshots.get(i);
            features[i] = new double[]{
                    value(snapshot.getRfmRecencyDays()),
                    value(snapshot.getRfmFrequency30d()),
                    number(snapshot.getRfmMonetary30d()),
                    number(snapshot.getOrderLunchRatio()),
                    number(snapshot.getOrderDinnerRatio()),
                    number(snapshot.getOrderNightRatio()),
                    number(snapshot.getPriceLowRatio()),
                    number(snapshot.getPriceMidRatio()),
                    number(snapshot.getPriceHighRatio())
            };
        }

        double[][] normalized = zScore(features);
        int k = Math.max(1, Math.min(clusterCount, normalized.length));
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
        return new Result(assignmentList, confidences, silhouette);
    }

    private static double[][] zScore(double[][] values) {
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

    private static List<double[]> initCentroids(double[][] values, int k) {
        List<double[]> centroids = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            centroids.add(values[i % values.length].clone());
        }
        return centroids;
    }

    private static boolean assignClusters(double[][] values, List<double[]> centroids, int[] assignments) {
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

    private static void recomputeCentroids(double[][] values, List<double[]> centroids, int[] assignments, int k) {
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

    private static double silhouetteScore(double[][] values, int[] assignments, int k) {
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

    private static double averageDistance(double[][] values, int[] assignments, int index, int clusterId) {
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

    private static double euclideanDistance(double[] left, double[] right) {
        double sum = 0D;
        for (int i = 0; i < left.length; i++) {
            sum += Math.pow(left[i] - right[i], 2);
        }
        return Math.sqrt(sum);
    }

}
