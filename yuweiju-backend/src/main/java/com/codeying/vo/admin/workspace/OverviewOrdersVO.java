package com.codeying.vo.admin.workspace;

import lombok.Data;

/**
 * Overview Orders VO view object.
 *
 * @author Endercloud
 */
@Data
public class OverviewOrdersVO {
    /** allOrders field. */
    private Integer allOrders;
    /** cancelledOrders field. */
    private Integer cancelledOrders;
    /** completedOrders field. */
    private Integer completedOrders;
    /** deliveredOrders field. */
    private Integer deliveredOrders;
    /** waitingOrders field. */
    private Integer waitingOrders;
}

