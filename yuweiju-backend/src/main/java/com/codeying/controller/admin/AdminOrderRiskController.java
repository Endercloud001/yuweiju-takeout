package com.codeying.controller.admin;

import com.codeying.dto.admin.order.OrderRiskFeedbackDTO;
import com.codeying.interceptor.JwtAuthInterceptor;
import com.codeying.result.ApiResult;
import com.codeying.service.OrderRiskService;
import com.codeying.vo.admin.order.OrderRiskModelMetaVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin order risk management endpoints.
 *
 * @author Endercloud
 */
@RestController
@RequestMapping("/admin/order-risk")
public class AdminOrderRiskController {

    private final OrderRiskService orderRiskService;

    public AdminOrderRiskController(OrderRiskService orderRiskService) {
        this.orderRiskService = orderRiskService;
    }

    /**
     * Trigger training job manually.
     *
     * @return operation result
     */
    @PostMapping("/training/run")
    public ApiResult<Map<String, Object>> runTraining() {
        orderRiskService.runScheduledTraining();
        return ApiResult.successData(Map.of("message", "order risk training triggered"));
    }

    /**
     * Trigger one-off backfill scoring for recent paid orders.
     *
     * @param days lookback days
     * @param limit max orders
     * @param onlyMissing whether only process orders without risk result
     * @return backfill summary
     */
    @PostMapping("/backfill/run")
    public ApiResult<Map<String, Object>> runBackfill(
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "onlyMissing", required = false) Boolean onlyMissing
    ) {
        return ApiResult.successData(orderRiskService.backfillRecentPaidOrders(days, limit, onlyMissing));
    }

    /**
     * Query training readiness before manual retraining.
     *
     * @return readiness summary
     */
    @GetMapping("/training/readiness")
    public ApiResult<Map<String, Object>> trainingReadiness() {
        return ApiResult.successData(orderRiskService.getTrainingReadiness());
    }

    /**
     * List model versions.
     *
     * @param limit max records
     * @return model metadata list
     */
    @GetMapping("/models")
    public ApiResult<List<OrderRiskModelMetaVO>> listModels(
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ApiResult.successData(orderRiskService.listModelVersions(limit));
    }

    /**
     * Query offline replay summary.
     *
     * @param modelVersion model version
     * @return replay summary
     */
    @GetMapping("/replay")
    public ApiResult<Map<String, Object>> replay(
            @RequestParam(value = "modelVersion", required = false) String modelVersion
    ) {
        return ApiResult.successData(orderRiskService.getReplaySummary(modelVersion));
    }

    /**
     * Submit review feedback and write back training label.
     *
     * @param dto review payload
     * @param request servlet request
     * @return operation result
     */
    @PostMapping("/feedback")
    public ApiResult<Map<String, Object>> submitFeedback(
            @Valid @RequestBody OrderRiskFeedbackDTO dto,
            HttpServletRequest request
    ) {
        Long operatorId = getAdminId(request);
        if (operatorId == null) {
            return ApiResult.unauthorized("unauthorized");
        }
        orderRiskService.submitFeedback(dto.getOrderId(), dto.getDecision(), operatorId, dto.getReason());
        return ApiResult.successData(Map.of(
                "orderId", dto.getOrderId(),
                "decision", dto.getDecision().trim().toLowerCase(),
                "operatorId", operatorId
        ));
    }

    private Long getAdminId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object adminIdObj = request.getAttribute(JwtAuthInterceptor.ATTR_ADMIN_ID);
        if (adminIdObj instanceof Long adminId) {
            return adminId;
        }
        return null;
    }
}
