package com.codeying.vo.common.ai_assistant;

import lombok.Data;

import java.util.Date;

/**
 * AI 助手消息 VO。
 *
 * @author Endercloud
 */
@Data
public class AiAssistantMessageVO {
    /** Primary key id. */
    private Long id;
    /** sessionId identifier. */
    private Long sessionId;
    /** senderType field. */
    private Integer senderType;
    /** senderId identifier. */
    private Long senderId;
    /** content field. */
    private String content;
    /** intent field. */
    private String intent;
    /** metadata field. */
    private String metadata;
    /** Time value. */
    private Date createTime;
}

