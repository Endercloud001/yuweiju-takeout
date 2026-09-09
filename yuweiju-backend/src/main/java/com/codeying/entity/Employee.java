package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 员工实体（管理端）。
 *
 * @author Endercloud
 */
@Data
@TableName("employee")
public class Employee implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("name")
    /** 员工姓名 */
    private String name;

    @TableField("username")
    /** 登录用户名 */
    private String username;

    @TableField("password")
    /** 登录密码（加密/明文取决于数据初始化方式） */
    private String password;

    @TableField("phone")
    /** 手机号 */
    private String phone;

    @TableField("sex")
    /** 性别：0-女 1-男（项目约定） */
    private String sex;

    @TableField("id_number")
    /** 身份证号 */
    private String idNumber;

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
