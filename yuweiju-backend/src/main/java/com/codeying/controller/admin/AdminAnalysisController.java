package com.codeying.controller.admin;

import com.codeying.dto.admin.analysis.AnalysisBackfillDTO;
import com.codeying.result.ApiResult;
import com.codeying.service.AnalysisApplicationService;
import com.codeying.service.AnalysisObservationService;
import com.codeying.vo.admin.analysis.DishHeatPredictionVO;
import com.codeying.vo.admin.analysis.UserClusterResultVO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理端热度分析接口。
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/analysis")
public class AdminAnalysisController {
    @org.springframework.web.bind.annotation.ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ApiResult<Object> invalidParameter(Exception failure) {
        return ApiResult.badRequest("分析日期或参数无效，请使用 yyyy-MM-dd 日期及有效范围");
    }


    @org.springframework.web.bind.annotation.ExceptionHandler(com.codeying.exception.BusinessException.class)
    public ApiResult<Object> businessFailure(com.codeying.exception.BusinessException failure) {
        return ApiResult.badRequest(failure.getMessage());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ApiResult<Object> dependencyFailure(Exception failure) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Analysis request unavailable, causeType={}", failure.getClass().getSimpleName());
        return ApiResult.fail("分析服务暂不可用，请检查依赖后重试");
    }

    private final AnalysisApplicationService analysisApplicationService;
    private final AnalysisObservationService analysisObservationService;

    public AdminAnalysisController(
            AnalysisApplicationService analysisApplicationService,
            AnalysisObservationService analysisObservationService
    ) {
        this.analysisApplicationService = analysisApplicationService;
        this.analysisObservationService = analysisObservationService;
    }

    /**
     * 手动触发特征快照生成。
     *
     * @param date 快照日期
     * @return 统一响应
     */
    @PostMapping("/snapshot/run")
    public ApiResult<Map<String, Object>> runSnapshot(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return response(analysisApplicationService.runSnapshotUseCase(date));
    }

    /**
     * 手动触发训练任务。
     *
     * @param date 训练快照日期
     * @return 统一响应
     */
    @PostMapping("/training/run")
    public ApiResult<Map<String, Object>> runTraining(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return response(analysisApplicationService.runManualTraining(date));
    }

    /**
     * 手动触发预测任务。
     *
     * @param date 预测窗口开始日期
     * @return 统一响应
     */
    @PostMapping("/prediction/run")
    public ApiResult<Map<String, Object>> runPrediction(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return response(analysisApplicationService.runPredictionUseCase(date));
    }

    private ApiResult<Map<String, Object>> response(com.codeying.vo.admin.analysis.AnalysisRun run) {
        Map<String, Object> data = run.response();
        if (!run.executed() || "FAILED".equals(run.status())) {
            return new ApiResult<>(0, false, (String) data.get("message"), data);
        }
        return ApiResult.successData(data);
    }

    /**
     * 执行历史快照回填。
     *
     * @param dto 请求参数
     * @return 统一响应
     */
    @PostMapping("/backfill")
    public ApiResult<Map<String, Object>> backfill(@Valid @RequestBody AnalysisBackfillDTO dto) {
        analysisApplicationService.backfillSnapshots(dto.endDate(), dto.days());
        return ApiResult.successData(Map.of("endDate", dto.endDate(), "days", dto.days(), "message", "回填任务完成"));
    }

    /**
     * 查询热度预测榜单。
     *
     * @param date 预测窗口开始日期
     * @param limit 返回条数
     * @return 榜单
     */
    @GetMapping("/heat")
    public ApiResult<List<DishHeatPredictionVO>> listHeatPredictions(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(value = "limit", required = false)
            Integer limit
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return ApiResult.successData(analysisApplicationService.listHeatPredictions(targetDate, limit));
    }

    /**
     * 查询用户聚类结果。
     *
     * @param date 聚类日期
     * @param limit 返回条数
     * @return 聚类结果
     */
    @GetMapping("/clusters")
    public ApiResult<List<UserClusterResultVO>> listUserClusters(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(value = "limit", required = false)
            Integer limit
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return ApiResult.successData(analysisApplicationService.listUserClusters(targetDate, limit));
    }

    /**
     * Get resource data.
     *
     * @param date date parameter
     * @return Object>> result
     */
    @GetMapping("/metrics/offline")
    public ApiResult<Map<String, Object>> getOfflineMetrics(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return ApiResult.successData(analysisApplicationService.getOfflineValidationMetrics(targetDate));
    }

    /**
     * Get resource data.
     *
     * @param date date parameter
     * @param modelVersion modelVersion parameter
     * @return Object>> result
     */
    @GetMapping("/metrics/online")
    public ApiResult<Map<String, Object>> getOnlineMetrics(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(value = "modelVersion", required = false)
            String modelVersion
    ) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return ApiResult.successData(analysisObservationService.getOnlineObservationSummary(targetDate, modelVersion));
    }
}
