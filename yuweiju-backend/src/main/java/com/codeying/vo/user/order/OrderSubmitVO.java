package com.codeying.vo.user.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户下单返回结果。
 *
 * @author Endercloud
 */
@Data
public class OrderSubmitVO {
    /** Primary key id. */
    private Long id;
    /** orderNumber field. */
    private String orderNumber;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date orderTime;
    /** Amount value. */
    private BigDecimal orderAmount;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date estimatedDeliveryTime;
}

