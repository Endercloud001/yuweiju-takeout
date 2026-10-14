package com.codeying.controller.admin;

import com.codeying.service.AnalysisApplicationService;
import com.codeying.service.AnalysisObservationService;
import com.codeying.vo.admin.analysis.AnalysisRun;
import com.codeying.vo.admin.analysis.AnalysisStage;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAnalysisControllerTest {
    @Test
    void delegatesCompletePipelineAndKeepsLegacyResponse() {
        var service = mock(AnalysisApplicationService.class);
        var date = LocalDate.of(2026, 6, 1);
        var run = new AnalysisRun(date);
        run.add(AnalysisStage.of("heatTraining", "COMPLETE", null, date, date, Map.of("modelVersion", "new-version")));
        run.put("heatModelTrained", true);
        when(service.runManualTraining(date)).thenReturn(run);
        var result = new AdminAnalysisController(service, mock(AnalysisObservationService.class)).runTraining(date);
        assertEquals(1, result.getCode());
        assertEquals(date, result.getData().get("snapshotDate"));
        assertEquals("COMPLETE", result.getData().get("status"));
        assertEquals(true, result.getData().get("heatModelTrained"));
        verify(service).runManualTraining(date);
        verifyNoMoreInteractions(service);
    }
    @Test
    void skippedTrainingRetainsCodeZeroAndStageDetails() {
        var service = mock(AnalysisApplicationService.class);
        var date = LocalDate.of(2026, 6, 2);
        var run = new AnalysisRun(date);
        run.executed(false);
        run.add(AnalysisStage.of("clustering", "SKIPPED", "NO_USER_SNAPSHOTS", date, date, Map.of()));
        when(service.runManualTraining(date)).thenReturn(run);
        var result = new AdminAnalysisController(service, mock(AnalysisObservationService.class)).runTraining(date);
        assertEquals(0, result.getCode());
        assertFalse(result.getSuccess());
        assertTrue(result.getMsg().contains("训练任务未执行"));
        assertNotNull(result.getData().get("stages"));
        verify(service).runManualTraining(date);
        verify(service, never()).runDailyPrediction(any());
    }
    @Test
    void predictionAcceptedWithoutDataKeepsCodeOneButReportsSkipped() {
        var service = mock(AnalysisApplicationService.class);
        var date = LocalDate.of(2026, 6, 2);
        var run = new AnalysisRun(date);
        run.put("windowStart", date);
        run.add(AnalysisStage.of("prediction", "SKIPPED", "NO_DISH_SNAPSHOTS", date, date, Map.of("predictionCount", 0)));
        when(service.runPredictionUseCase(date)).thenReturn(run);
        var result = new AdminAnalysisController(service, mock(AnalysisObservationService.class)).runPrediction(date);
        assertEquals(1, result.getCode());
        assertEquals("SKIPPED", result.getData().get("status"));
        assertEquals(date, result.getData().get("windowStart"));
    }
}
