package com.codeying.dto.user.order;

import lombok.Data;

/**
 * 用户支付请求参数。
 *
 * @author Endercloud
 */
@Data
public class OrdersPaymentDTO {
    /** 订单号 */
    private String orderNumber;
    /** 支付方式：1-微信 */
    private Integer payMethod;
}

