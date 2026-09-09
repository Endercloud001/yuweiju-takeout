package com.codeying.vo.admin.order;

import lombok.Data;

/**
 * 管理端订单统计 VO。
 *
 * @author Endercloud
 */
@Data
public class OrderStatisticsVO {
    /** toBeConfirmed field. */
    private Integer toBeConfirmed;
    /** confirmed field. */
    private Integer confirmed;
    /** deliveryInProgress field. */
    private Integer deliveryInProgress;
}

