package com.codeying.service;

import com.codeying.vo.admin.analysis.DishHeatPredictionVO;
import com.codeying.vo.admin.analysis.UserClusterResultVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 热度分析与数据挖掘应用服务。
 *
 * @author Endercloud
 */
public interface AnalysisApplicationService {

    /**
     * 生成单日特征快照。
     *
     * @param snapshotDate 快照日期
     */
    void generateDailySnapshots(LocalDate snapshotDate);

    /**
     * 执行模型训练与用户聚类。
     *
     * @param snapshotDate 快照日期
     * @return 训练执行结果
     */
    WeeklyTrainingResult runWeeklyTraining(LocalDate snapshotDate);

    /**
     * 生成菜品热度预测结果。
     *
     * @param windowStart 预测窗口开始日期
     */
    void runDailyPrediction(LocalDate windowStart);

    /**
     * 执行历史快照回填。
     *
     * @param endDate 结束日期
     * @param days    回填天数
     */
    void backfillSnapshots(LocalDate endDate, Integer days);

    /**
     * 查询热度预测结果。
     *
     * @param windowStart 预测窗口开始日期
     * @param limit       返回条数
     * @return 预测结果列表
     */
    List<DishHeatPredictionVO> listHeatPredictions(LocalDate windowStart, Integer limit);

    /**
     * 查询用户聚类结果。
     *
     * @param snapshotDate 快照日期
     * @param limit        返回条数
     * @return 聚类结果列表
     */
    List<UserClusterResultVO> listUserClusters(LocalDate snapshotDate, Integer limit);

    /**
     * 查询用户推荐候选菜品。
     *
     * @param userId 用户 ID
     * @param limit  返回条数
     * @return 菜品 ID 列表
     */
    List<Long> listRecommendedDishIdsForUser(Long userId, Integer limit);

    /**
     * 查询离线验证指标。
     * @param snapshotDate 快照日期
     * @return 指标结果
     */
    Map<String, Object> getOfflineValidationMetrics(LocalDate snapshotDate);

    /**
     * 周训练执行结果。
     *
     * @param snapshotDate      快照日期
     * @param executed          是否实际执行训练
     * @param userSnapshotCount 用户快照数量
     * @param clusterCount      分群数量
     * @param clusterModelVersion 分群模型版本
     * @param heatModelTrained  是否完成热度模型训练
     * @param skipReason        跳过原因（未跳过时为 null）
     */
    record WeeklyTrainingResult(
            LocalDate snapshotDate,
            boolean executed,
            int userSnapshotCount,
            int clusterCount,
            String clusterModelVersion,
            boolean heatModelTrained,
            String skipReason
    ) {}
}
