package com.codeying.vo.user.order;

import com.codeying.entity.OrderDetail;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 用户端订单详情 VO。
 *
 * @author Endercloud
 */
@Data
public class OrderVO {
    /** Primary key id. */
    private Long id;
    /** number field. */
    private String number;
    /** Status value. */
    private Integer status;
    /** userId identifier. */
    private Long userId;
    /** addressBookId identifier. */
    private Long addressBookId;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date orderTime;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date checkoutTime;
    /** payMethod field. */
    private Integer payMethod;
    /** Status value. */
    private Integer payStatus;
    /** Amount value. */
    private BigDecimal amount;
    /** remark field. */
    private String remark;
    /** Phone number. */
    private String phone;
    /** Address detail. */
    private String address;
    /** Display name. */
    private String userName;
    /** consignee field. */
    private String consignee;
    /** cancelReason field. */
    private String cancelReason;
    /** rejectionReason field. */
    private String rejectionReason;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date cancelTime;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date estimatedDeliveryTime;
    /** Status value. */
    private Integer deliveryStatus;
    /** Time value. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date deliveryTime;
    /** Amount value. */
    private Integer packAmount;
    /** tablewareNumber field. */
    private Integer tablewareNumber;
    /** Status value. */
    private Integer tablewareStatus;
    /** orderDetailList field. */
    private List<OrderDetail> orderDetailList;
    /** orderDishes field. */
    private String orderDishes;
}

