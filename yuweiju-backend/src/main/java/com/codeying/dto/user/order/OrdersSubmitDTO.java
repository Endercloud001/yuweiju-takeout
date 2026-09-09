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
    /** 配送状态：1-立即送出（项目约定） */
    private Integer deliveryStatus;
    /** 预计送达时间（客户端展示用，后端重新计算） */
    private String estimatedDeliveryTime;
    /** 打包费 */
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

