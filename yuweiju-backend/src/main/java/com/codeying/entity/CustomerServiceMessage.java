package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 人工客服消息实体。
 *
 * @author Endercloud
 */
@Data
@TableName("customer_service_message")
public class CustomerServiceMessage implements Serializable {

    /** 发送方类型：1-用户 */
    public static final int SENDER_USER = 1;
    /** 发送方类型：2-管理员 */
    public static final int SENDER_ADMIN = 2;
    /** 发送方类型：3-系统 */
    public static final int SENDER_SYSTEM = 3;

    @TableId(type = IdType.AUTO)
    /** 主键 ID */
    private Long id;

    @TableField("session_id")
    /** 会话 ID */
    private Long sessionId;

    @TableField("sender_type")
    /** 发送方类型：1-用户 2-管理员 3-系统 */
    private Integer senderType;

    @TableField("sender_id")
    /** 发送方 ID（系统消息为空） */
    private Long senderId;

    @TableField("content")
    /** 消息内容 */
    private String content;

    @TableField("create_time")
    /** 创建时间 */
    private Date createTime;
}
