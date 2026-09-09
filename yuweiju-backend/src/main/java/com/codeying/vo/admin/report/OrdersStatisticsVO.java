package com.codeying.vo.admin.report;

import lombok.Data;

/**
 * Orders Statistics VO view object.
 *
 * @author Endercloud
 */
@Data
public class OrdersStatisticsVO {
    /** dateList field. */
    private String dateList;
    /** orderCompletionRate field. */
    private Double orderCompletionRate;
    /** orderCountList field. */
    private String orderCountList;
    /** totalOrderCount field. */
    private Integer totalOrderCount;
    /** validOrderCount field. */
    private Integer validOrderCount;
    /** validOrderCountList field. */
    private String validOrderCountList;
}

