package com.codeying.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 助手会话实体。
 *
 * @author Endercloud
 */
@Data
@TableName("ai_assistant_session")
public class AiAssistantSession implements Serializable {

    /** 会话状态：进行中。 */
    public static final int STATUS_OPEN = 1;
    /** 会话状态：已关闭。 */
    public static final int STATUS_CLOSED = 2;

    @TableId(type = IdType.AUTO)
    /** 主键 ID。 */
    private Long id;

    @TableField("user_id")
    /** 用户 ID。 */
    private Long userId;

    @TableField("status")
    /** 会话状态。 */
    private Integer status;

    @TableField("last_intent")
    /** 最近意图。 */
    private String lastIntent;

    @TableField("pending_action")
    /** 待处理动作。 */
    private String pendingAction;

    @TableField("pending_payload")
    /** 待处理动作参数（JSON 字符串）。 */
    private String pendingPayload;

    @TableField("model")
    /** 模型标识。 */
    private String model;

    @TableField("create_time")
    /** 创建时间。 */
    private Date createTime;

    @TableField("update_time")
    /** 更新时间。 */
    private Date updateTime;

    @TableField("close_time")
    /** 关闭时间。 */
    private Date closeTime;

    @TableField("close_reason")
    /** 关闭原因。 */
    private String closeReason;
}

