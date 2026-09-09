package com.codeying.task;

import com.codeying.properties.AnalysisProperties;
import com.codeying.service.AnalysisApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 热度分析定时任务。
 *
 * @author Endercloud
 */
@Slf4j
@Component
public class AnalysisTask {

    private final AnalysisApplicationService analysisApplicationService;
    private final AnalysisProperties analysisProperties;

    public AnalysisTask(
            AnalysisApplicationService analysisApplicationService,
            AnalysisProperties analysisProperties
    ) {
        this.analysisApplicationService = analysisApplicationService;
        this.analysisProperties = analysisProperties;
    }

    /**
     * 每日生成特征快照。
     */
    @Scheduled(cron = "${analysis.task.snapshot-cron}")
    public void generateSnapshots() {
        if (!Boolean.TRUE.equals(analysisProperties.getTask().getEnabled())) {
            return;
        }
        LocalDate snapshotDate = LocalDate.now();
        log.info("开始执行分析快照任务，snapshotDate={}", snapshotDate);
        analysisApplicationService.generateDailySnapshots(snapshotDate);
    }

    /**
     * 每日生成热度预测。
     */
    @Scheduled(cron = "${analysis.task.prediction-cron}")
    public void generatePredictions() {
        if (!Boolean.TRUE.equals(analysisProperties.getTask().getEnabled())) {
            return;
        }
        LocalDate windowStart = LocalDate.now();
        log.info("开始执行热度预测任务，windowStart={}", windowStart);
        analysisApplicationService.runDailyPrediction(windowStart);
    }

    /**
     * 每周执行模型训练与用户聚类。
     */
    @Scheduled(cron = "${analysis.task.training-cron}")
    public void runTraining() {
        if (!Boolean.TRUE.equals(analysisProperties.getTask().getEnabled())) {
            return;
        }
        LocalDate snapshotDate = LocalDate.now();
        log.info("开始执行模型训练任务，snapshotDate={}", snapshotDate);
        AnalysisApplicationService.WeeklyTrainingResult result = analysisApplicationService.runWeeklyTraining(snapshotDate);
        if (!result.executed()) {
            log.warn("模型训练任务跳过，snapshotDate={}, reason={}", result.snapshotDate(), result.skipReason());
        }
    }
}
