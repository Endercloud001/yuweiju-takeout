package com.codeying.controller.admin;

import com.codeying.result.ApiResult;
import com.codeying.service.AnalysisApplicationService;
import com.codeying.service.AnalysisObservationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAnalysisControllerTest {

    @Test
    void shouldRunManualRetrainPipelineAndReturnSuccess() {
        AnalysisApplicationService analysisApplicationService = Mockito.mock(AnalysisApplicationService.class);
        AnalysisObservationService analysisObservationService = Mockito.mock(AnalysisObservationService.class);
        AdminAnalysisController controller = new AdminAnalysisController(analysisApplicationService, analysisObservationService);
        LocalDate snapshotDate = LocalDate.of(2026, 6, 1);
        Mockito.when(analysisApplicationService.runWeeklyTraining(snapshotDate))
                .thenReturn(new AnalysisApplicationService.WeeklyTrainingResult(
                        snapshotDate,
                        true,
                        13,
                        3,
                        "analysis-cluster-2026-06-01-123",
                        true,
                        null
                ));

        ApiResult<Map<String, Object>> result = controller.runTraining(snapshotDate);

        assertEquals(1, result.getCode());
        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        assertNotNull(result.getData());
        assertEquals(snapshotDate, result.getData().get("snapshotDate"));
        assertTrue(String.valueOf(result.getData().get("message")).contains("重训任务完成"));
        Mockito.verify(analysisApplicationService).generateDailySnapshots(snapshotDate);
        Mockito.verify(analysisApplicationService).runWeeklyTraining(snapshotDate);
        Mockito.verify(analysisApplicationService).runDailyPrediction(snapshotDate);
    }

    @Test
    void shouldReturnBadRequestWhenTrainingSkipped() {
        AnalysisApplicationService analysisApplicationService = Mockito.mock(AnalysisApplicationService.class);
        AnalysisObservationService analysisObservationService = Mockito.mock(AnalysisObservationService.class);
        AdminAnalysisController controller = new AdminAnalysisController(analysisApplicationService, analysisObservationService);
        LocalDate snapshotDate = LocalDate.of(2026, 6, 2);
        Mockito.when(analysisApplicationService.runWeeklyTraining(snapshotDate))
                .thenReturn(new AnalysisApplicationService.WeeklyTrainingResult(
                        snapshotDate,
                        false,
                        0,
                        0,
                        null,
                        false,
                        "NO_USER_SNAPSHOTS"
                ));

        ApiResult<Map<String, Object>> result = controller.runTraining(snapshotDate);

        assertEquals(0, result.getCode());
        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        assertTrue(String.valueOf(result.getMsg()).contains("训练任务未执行"));
        Mockito.verify(analysisApplicationService).generateDailySnapshots(snapshotDate);
        Mockito.verify(analysisApplicationService).runWeeklyTraining(snapshotDate);
        Mockito.verify(analysisApplicationService, Mockito.never()).runDailyPrediction(Mockito.any());
    }
}

