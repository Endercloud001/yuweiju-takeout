package com.codeying.service;

import com.codeying.entity.OrderRiskResult;
import com.codeying.vo.admin.order.OrderRiskModelMetaVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Order risk scoring service.
 *
 * @author Endercloud
 */
public interface OrderRiskService {

    /**
     * Score the order and persist snapshot/result.
     *
     * @param orderId order id
     * @param trigger trigger source
     */
    void scoreOrder(Long orderId, String trigger);

    /**
     * Batch load latest risk result for orders.
     *
     * @param orderIds order ids
     * @return latest result map
     */
    Map<Long, OrderRiskResult> findLatestByOrderIds(Collection<Long> orderIds);

    /**
     * Build lightweight reason summary for page list.
     *
     * @param result risk result
     * @return reason summary text
     */
    String summarizeReason(OrderRiskResult result);

    /**
     * Scheduled training entry.
     */
    void runScheduledTraining();

    /**
     * Backfill score for recent paid orders.
     *
     * @param days lookback days
     * @param limit max orders
     * @param onlyMissing only score orders without existing risk result
     * @return execution summary
     */
    Map<String, Object> backfillRecentPaidOrders(Integer days, Integer limit, Boolean onlyMissing);

    /**
     * Query current training readiness summary.
     *
     * @return readiness details and counts
     */
    Map<String, Object> getTrainingReadiness();

    /**
     * List model metadata for admin.
     *
     * @param limit max records
     * @return model metadata list
     */
    List<OrderRiskModelMetaVO> listModelVersions(Integer limit);

    /**
     * Query offline replay metrics by model version.
     *
     * @param modelVersion model version, null for latest active
     * @return replay summary map
     */
    Map<String, Object> getReplaySummary(String modelVersion);

    /**
     * Submit admin risk review feedback.
     *
     * @param orderId order id
     * @param decision review decision
     * @param operatorId operator id
     * @param reason review reason
     */
    void submitFeedback(Long orderId, String decision, Long operatorId, String reason);
}
