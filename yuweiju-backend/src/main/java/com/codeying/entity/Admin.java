package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

import java.io.Serializable;

/**
 * 管理员实体。
 *
 * @author Endercloud
 */
@Data
@TableName("tb_admin")
public class Admin extends LoginUser implements Serializable {

    /** 角色 */
    @TableField(exist = false)
    private String role = "admin";

    /** 角色中文名 */
    @TableField(exist = false)
    private String rolech = "管理员";

    /** 管理员主键 */
    @TableId
    private String id;

    /** 用户名 */
    @TableField("username")
    private String username;

    /** 密码 */
    @TableField("password")
    private String password;

    /** 姓名 */
    @TableField("name")
    private String name;

    /** 电话 */
    @TableField("tele")
    private String tele;

    /** 创建时间 */
    @TableField("createtime")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createtime;
}

