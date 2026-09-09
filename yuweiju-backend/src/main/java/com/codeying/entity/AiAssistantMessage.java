package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 助手消息实体。
 *
 * @author Endercloud
 */
@Data
@TableName("ai_assistant_message")
public class AiAssistantMessage implements Serializable {

    /** 发送方：用户。 */
    public static final int SENDER_USER = 1;
    /** 发送方：AI。 */
    public static final int SENDER_AI = 2;
    /** 发送方：系统。 */
    public static final int SENDER_SYSTEM = 3;

    @TableId(type = IdType.AUTO)
    /** 主键 ID。 */
    private Long id;

    @TableField("session_id")
    /** 会话 ID。 */
    private Long sessionId;

    @TableField("sender_type")
    /** 发送方类型。 */
    private Integer senderType;

    @TableField("sender_id")
    /** 发送方 ID。 */
    private Long senderId;

    @TableField("content")
    /** 消息内容。 */
    private String content;

    @TableField("intent")
    /** 意图。 */
    private String intent;

    @TableField("metadata")
    /** 元数据（JSON 字符串）。 */
    private String metadata;

    @TableField("create_time")
    /** 创建时间。 */
    private Date createTime;
}

