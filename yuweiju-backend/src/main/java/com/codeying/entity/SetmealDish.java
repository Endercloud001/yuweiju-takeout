package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 套餐与菜品关联实体。
 *
 * @author Endercloud
 */
@Data
@TableName("setmeal_dish")
public class SetmealDish implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("setmeal_id")
    /** 套餐 ID */
    private Long setmealId;

    @TableField("dish_id")
    /** 菜品 ID */
    private Long dishId;

    @TableField("name")
    /** 菜品名称（冗余字段，用于展示） */
    private String name;

    @TableField("price")
    /** 菜品价格（单位：元） */
    private BigDecimal price;

    @TableField("copies")
    /** 份数 */
    private Integer copies;
}
