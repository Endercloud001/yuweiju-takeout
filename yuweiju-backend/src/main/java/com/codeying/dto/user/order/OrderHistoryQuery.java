package com.codeying.dto.user.order;

import com.codeying.dto.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Order History Query 
 *
 * @author Endercloud
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderHistoryQuery extends PageQuery {
    /**  */
    private Integer status;
}

