package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 分类实体（菜品分类/套餐分类）。
 *
 * @author Endercloud
 */
@Data
@TableName("category")
public class Category implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("type")
    /** 类型：1-菜品分类 2-套餐分类 */
    private Integer type;

    @TableField("name")
    /** 分类名称 */
    private String name;

    @TableField("sort")
    /** 排序值 */
    private Integer sort;

    @TableField("status")
    /** 状态：1-启用 0-禁用（项目约定） */
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

