package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体。
 *
 * @author Endercloud
 */
@Data
@TableName("user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("openid")
    /** 微信 openid */
    private String openid;

    @TableField("name")
    /** 用户姓名/昵称 */
    private String name;

    @TableField("phone")
    /** 手机号 */
    private String phone;

    @TableField("sex")
    /** 性别：0-女 1-男（项目约定） */
    private String sex;

    @TableField("id_number")
    /** 身份证号 */
    private String idNumber;

    @TableField("avatar")
    /** 头像地址 */
    private String avatar;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;
}
