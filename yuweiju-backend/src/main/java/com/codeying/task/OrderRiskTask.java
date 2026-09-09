package com.codeying.task;

import com.codeying.properties.OrderRiskProperties;
import com.codeying.service.OrderRiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Order risk scheduled tasks.
 *
 * @author Endercloud
 */
@Slf4j
@Component
public class OrderRiskTask {

    private final OrderRiskService orderRiskService;
    private final OrderRiskProperties orderRiskProperties;

    public OrderRiskTask(OrderRiskService orderRiskService, OrderRiskProperties orderRiskProperties) {
        this.orderRiskService = orderRiskService;
        this.orderRiskProperties = orderRiskProperties;
    }

    /**
     * Weekly training trigger.
     */
    @Scheduled(cron = "${order-risk.task.training-cron}")
    public void runTraining() {
        if (!Boolean.TRUE.equals(orderRiskProperties.getTask().getScoringEnabled())) {
            return;
        }
        log.info("Order risk training task triggered.");
        orderRiskService.runScheduledTraining();
    }
}

