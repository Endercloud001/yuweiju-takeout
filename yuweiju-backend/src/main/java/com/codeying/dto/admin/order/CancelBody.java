package com.codeying.dto.admin.order;

import lombok.Data;

/**
 * 管理端取消订单请求体。
 *
 * @author Endercloud
 */
@Data
public class CancelBody {
    /**  ID */
    private Long id;
    /** cancelReason  */
    private String cancelReason;
}

