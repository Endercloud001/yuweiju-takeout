package com.codeying.service;

import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Optional recommendation observations: Redis failures are logged as unavailable and
 * stop that observation without failing the caller. No success receipt or retry is
 * implied. Partial Redis writes are not covered by the caller's SQL transaction.
 *
 * @author Endercloud
 */
public interface AnalysisObservationService {

    /**
     * Execute recordRecommendationExposure.
     *
     * @param userId business identifier
     * @param sessionId business identifier
     * @param messageId business identifier
     * @param replyVO replyVO parameter
     */
    void recordRecommendationExposure(Long userId, Long sessionId, Long messageId, AiAssistantSendReplyVO replyVO);

    /**
     * Execute recordRecommendationClick.
     *
     * @param userId business identifier
     * @param dishId business identifier
     */
    void recordRecommendationClick(Long userId, Long dishId);

    /**
     * Execute recordRecommendationConversion.
     *
     * @param userId business identifier
     * @param orderId business identifier
     * @param dishIds business identifier list
     */
    void recordRecommendationConversion(Long userId, Long orderId, List<Long> dishIds);

    /**
     * Execute logOnlineObservation.
     *
     * @param observeDate observeDate parameter
     * @param modelVersion modelVersion parameter
     */
    void logOnlineObservation(LocalDate observeDate, String modelVersion);

    /**
     * Get resource data.
     *
     * @param observeDate observeDate parameter
     * @param modelVersion modelVersion parameter
     * @return Object> result
     */
    Map<String, Object> getOnlineObservationSummary(LocalDate observeDate, String modelVersion);
}
