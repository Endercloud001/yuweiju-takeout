package com.codeying.dto.user.order;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户下单请求参数。
 *
 * @author Endercloud
 */
@Data
public class OrdersSubmitDTO {
    /** 收货地址簿 ID */
    private Long addressBookId;
    /** 金额（前端可传，后端以计算为准） */
    private BigDecimal amount;
    /** 兼容旧配送标记（含 0-预约）；新单统一保存 1-立即送出 */
    private Integer deliveryStatus;
    /** 兼容旧客户端时间字符串；忽略其值，预计送达由服务端重新计算 */
    private String estimatedDeliveryTime;
    /** 客户端打包费声明（忽略；服务端按有效总份数计算） */
    private Integer packAmount;
    /** 支付方式：1-微信 */
    private Integer payMethod;
    /** 备注 */
    private String remark;
    /** 餐具数量 */
    private Integer tablewareNumber;
    /** 餐具状态：1-按餐量/0-无需 */
    private Integer tablewareStatus;
}

