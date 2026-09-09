package com.codeying.vo.admin.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Report Business Data.
 *
 * @author Endercloud
 */
@Data
public class ReportBusinessData {
    /** turnover field. */
    private BigDecimal turnover;
    /** orderCompletionRate field. */
    private double orderCompletionRate;
    /** newUsers field. */
    private int newUsers;
    /** validOrderCount field. */
    private int validOrderCount;
    /** Amount value. */
    private BigDecimal unitPrice;
}

