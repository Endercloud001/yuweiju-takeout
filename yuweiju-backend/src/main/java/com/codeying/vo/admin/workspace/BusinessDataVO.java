package com.codeying.vo.admin.workspace;

import lombok.Data;

/**
 * Business Data VO view object.
 *
 * @author Endercloud
 */
@Data
public class BusinessDataVO {
    /** newUsers field. */
    private Integer newUsers;
    /** orderCompletionRate field. */
    private Double orderCompletionRate;
    /** turnover field. */
    private Double turnover;
    /** Amount value. */
    private Double unitPrice;
    /** validOrderCount field. */
    private Integer validOrderCount;
}

