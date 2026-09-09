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
 * 菜品实体。
 *
 * @author Endercloud
 */
@Data
@TableName("dish")
public class Dish implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("name")
    /** 菜品名称 */
    private String name;

    @TableField("category_id")
    /** 分类 ID */
    private Long categoryId;

    @TableField("price")
    /** 菜品价格（单位：元） */
    private BigDecimal price;

    @TableField("image")
    /** 图片 URL */
    private String image;

    @TableField("description")
    /** 描述 */
    private String description;

    @TableField("status")
    /** 售卖状态：1-起售 0-停售 */
    private Integer status;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;

    @TableField("update_time")
    /** 更新时间 */
    private Date updateTime;

    @TableField("create_user")
    /** 创建人 ID */
    private Long createUser;

    @TableField("update_user")
    /** 更新人 ID */
    private Long updateUser;
}
