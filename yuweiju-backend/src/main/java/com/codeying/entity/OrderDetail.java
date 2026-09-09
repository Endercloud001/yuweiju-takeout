package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细实体。
 *
 * @author Endercloud
 */
@Data
@TableName("order_detail")
public class OrderDetail implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("name")
    /** 商品名称（菜品名/套餐名） */
    private String name;

    @TableField("image")
    /** 图片 URL */
    private String image;

    @TableField("order_id")
    /** 订单 ID */
    private Long orderId;

    @TableField("dish_id")
    /** 菜品 ID（菜品明细时有值） */
    private Long dishId;

    @TableField("setmeal_id")
    /** 套餐 ID（套餐明细时有值） */
    private Long setmealId;

    @TableField("dish_flavor")
    /** 口味信息（项目约定的字符串） */
    private String dishFlavor;

    @TableField("number")
    /** 数量 */
    private Integer number;

    @TableField("amount")
    /** 金额（单位：元） */
    private BigDecimal amount;
}
