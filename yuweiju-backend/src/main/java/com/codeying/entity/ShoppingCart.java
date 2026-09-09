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
 * 购物车实体。
 *
 * @author Endercloud
 */
@Data
@TableName("shopping_cart")
public class ShoppingCart implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("name")
    /** 商品名称（菜品/套餐） */
    private String name;

    @TableField("image")
    /** 图片 URL */
    private String image;

    @TableField("user_id")
    /** 用户 ID */
    private Long userId;

    @TableField("dish_id")
    /** 菜品 ID */
    private Long dishId;

    @TableField("setmeal_id")
    /** 套餐 ID */
    private Long setmealId;

    @TableField("dish_flavor")
    /** 口味信息 */
    private String dishFlavor;

    @TableField("number")
    /** 数量 */
    private Integer number;

    @TableField("amount")
    /** 金额（单位：元） */
    private BigDecimal amount;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;
}
