package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单实体。
 *
 * @author Endercloud
 */
@Data
@TableName("orders")
public class Orders implements Serializable {

    /** 订单状态：1-待付款 */
    public static final int PENDING_PAYMENT = 1;
    /** 订单状态：2-待接单 */
    public static final int TO_BE_CONFIRMED = 2;
    /** 订单状态：3-已接单 */
    public static final int CONFIRMED = 3;
    /** 订单状态：4-派送中 */
    public static final int DELIVERY_IN_PROGRESS = 4;
    /** 订单状态：5-已完成 */
    public static final int COMPLETED = 5;
    /** 订单状态：6-已取消 */
    public static final int CANCELLED = 6;

    /** 支付状态：0-未支付 */
    public static final int UN_PAID = 0;
    /** 支付状态：1-已支付 */
    public static final int PAID = 1;

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("number")
    /** 订单号 */
    private String number;

    @TableField("status")
    /** 订单状态：1-待付款 2-待接单 3-已接单 4-派送中 5-已完成 6-已取消 */
    private Integer status;

    @TableField("user_id")
    /** 下单用户 ID */
    private Long userId;

    @TableField("address_book_id")
    /** 收货地址簿 ID */
    private Long addressBookId;

    @TableField("order_time")
    /** 下单时间 */
    private Date orderTime;

    @TableField("checkout_time")
    /** 结账时间（支付完成时间） */
    private Date checkoutTime;

    @TableField("pay_method")
    /** 支付方式：1-微信支付（当前项目） */
    private Integer payMethod;

    @TableField("pay_status")
    /** 支付状态：0-未支付 1-已支付 */
    private Integer payStatus;

    @TableField("amount")
    /** 实收金额（单位：元），精确到分 */
    private BigDecimal amount;

    @TableField("remark")
    /** 订单备注 */
    private String remark;

    @TableField("phone")
    /** 联系电话 */
    private String phone;

    @TableField("address")
    /** 收货地址（拼接后的完整地址文本） */
    private String address;

    @TableField("user_name")
    /** 下单用户名称 */
    private String userName;

    @TableField("consignee")
    /** 收货人 */
    private String consignee;

    @TableField("cancel_reason")
    /** 取消原因（用户取消/系统取消等） */
    private String cancelReason;

    @TableField("rejection_reason")
    /** 拒单原因（商家拒单） */
    private String rejectionReason;

    @TableField("cancel_time")
    /** 取消时间 */
    private Date cancelTime;

    @TableField("estimated_delivery_time")
    /** 预计送达时间 */
    private Date estimatedDeliveryTime;

    @TableField("delivery_status")
    /** 配送状态：1-立即送出（项目约定） */
    private Integer deliveryStatus;

    @TableField("delivery_time")
    /** 实际送达时间 */
    private Date deliveryTime;

    @TableField("pack_amount")
    /** 打包费（单位：元） */
    private Integer packAmount;

    @TableField("tableware_number")
    /** 餐具数量 */
    private Integer tablewareNumber;

    @TableField("tableware_status")
    /** 餐具状态：1-按餐量提供/0-无需餐具（项目约定） */
    private Integer tablewareStatus;
}
