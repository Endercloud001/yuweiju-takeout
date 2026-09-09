package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 菜品口味实体。
 *
 * @author Endercloud
 */
@Data
@TableName("dish_flavor")
public class DishFlavor implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("dish_id")
    /** 菜品 ID */
    private Long dishId;

    @TableField("name")
    /** 口味名称（如 辣度/甜度） */
    private String name;

    @TableField("value")
    /** 口味值（项目约定，通常为可选值列表的字符串） */
    private String value;
}
