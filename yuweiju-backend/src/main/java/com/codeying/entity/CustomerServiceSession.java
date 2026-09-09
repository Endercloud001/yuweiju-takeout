package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 人工客服会话实体。
 *
 * @author Endercloud
 */
@Data
@TableName("customer_service_session")
public class CustomerServiceSession implements Serializable {

    /** 会话状态：1-进行中 */
    public static final int STATUS_OPEN = 1;
    /** 会话状态：2-已关闭 */
    public static final int STATUS_CLOSED = 2;

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("user_id")
    /** 用户 ID */
    private Long userId;

    @TableField("status")
    /** 会话状态：1-进行中 2-已关闭 */
    private Integer status;

    @TableField("assigned_admin_id")
    /** 绑定管理员 ID（可为空） */
    private Long assignedAdminId;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;

    @TableField("update_time")
    /** 更新时间（最近一条消息时间/状态变更时间） */
    private Date updateTime;

    @TableField("close_time")
    /** 关闭时间 */
    private Date closeTime;

    @TableField("close_reason")
    /** 关闭原因 */
    private String closeReason;
}
