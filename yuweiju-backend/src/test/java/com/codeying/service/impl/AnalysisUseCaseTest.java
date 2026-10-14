package com.codeying.service.impl;

import com.codeying.analysis.HeatModelStore;
import com.codeying.mapper.*;
import com.codeying.properties.AnalysisProperties;
import com.codeying.service.*;
import com.codeying.vo.admin.analysis.AnalysisRun;
import com.codeying.vo.admin.analysis.AnalysisStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisUseCaseTest {
    private AnalysisApplicationServiceImpl service;
    private UserBehaviorFeatureSnapshotMapper users;
    private DishTimeseriesFeatureSnapshotMapper dishes;
    private DishHeatPredictionResultMapper predictions;
    private final LocalDate date = LocalDate.of(2026, 6, 2);
    @BeforeEach void setup() {
        var props = new AnalysisProperties(); props.setFeatureVersion("v1"); props.setPredictionWindowDays(7);
        users = mock(UserBehaviorFeatureSnapshotMapper.class); dishes = mock(DishTimeseriesFeatureSnapshotMapper.class);
        predictions = mock(DishHeatPredictionResultMapper.class);
        service = spy(new AnalysisApplicationServiceImpl(new com.codeying.analysis.HeatFeatures(props), mock(AnalysisFeatureMapper.class), mock(PlatformTransactionManager.class), mock(HeatModelStore.class),
                mock(OrdersMapper.class), mock(OrderDetailMapper.class), mock(DishService.class), mock(DishFlavorService.class),
                mock(UserService.class), mock(AddressBookMapper.class), users, dishes, predictions, mock(UserClusterResultMapper.class),
                mock(StringRedisTemplate.class), new ObjectMapper(), props, mock(AiWeatherService.class), mock(AnalysisObservationService.class)));
    }
    @Test void missingUsersStopsPredictionEvenAfterSnapshotStage() {
        var snapshot = new AnalysisRun(date);
        snapshot.add(AnalysisStage.of("snapshot", "PARTIAL", "NO_USER_SNAPSHOTS", date, date, Map.of()));
        doReturn(snapshot).when(service).runSnapshotUseCase(date);
        when(users.findDaily(date,"v1")).thenReturn(List.of());
        var result = service.runManualTraining(date);
        assertFalse(result.executed());
        assertEquals("NO_USER_SNAPSHOTS", result.response().get("skipReason"));
        assertTrue(result.stages().stream().anyMatch(s -> "TRAINING_NOT_EXECUTED".equals(s.reason())));
        verify(service,never()).runPredictionUseCase(any());
    }
    @Test void missingDishesSkipsWithoutDeletingPredictionsOrLoadingModel() {
        when(dishes.findDaily(date,"v1")).thenReturn(List.of());
        var result = service.runPredictionUseCase(date);
        assertEquals("SKIPPED",result.status());
        assertEquals("NO_DISH_SNAPSHOTS",result.stages().get(0).reason());
        verifyNoInteractions(predictions);
    }
    @Test void dependencyFailureIsTruthfulAndSanitized() throws Exception {
        when(dishes.findDaily(date,"v1")).thenThrow(new IllegalStateException("jdbc://private-detail"));
        var result=service.runPredictionUseCase(date);
        assertEquals("FAILED",result.status());
        assertFalse(result.stages().get(0).outputs().toString().contains("private-detail"));
        assertEquals("jdbc://private-detail", result.stages().get(0).failureCause().getCause().getMessage());
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(result.response()).contains("private-detail"));
        verifyNoInteractions(predictions);
    }
    @Test void rejectsInvalidDateAndBackfillRangeBeforeWrites() {
        assertThrows(com.codeying.exception.BusinessException.class, () -> service.runPredictionUseCase(LocalDate.of(9999,12,31)));
        assertThrows(com.codeying.exception.BusinessException.class, () -> service.backfillSnapshots(date, 0));
        assertThrows(com.codeying.exception.BusinessException.class, () -> service.backfillSnapshots(date, 366));
        verifyNoInteractions(users,dishes,predictions);
    }
}
