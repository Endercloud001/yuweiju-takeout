package com.codeying.vo.admin.ai_assistant;

import lombok.Data;

import java.util.Date;

/**
 * 管理端 AI 助手会话摘要 VO。
 *
 * @author Endercloud
 */
@Data
public class AiAssistantSessionSummaryVO {
    /** Primary key id. */
    private Long id;
    /** userId identifier. */
    private Long userId;
    /** Display name. */
    private String userName;
    /** userAvatar field. */
    private String userAvatar;
    /** Status value. */
    private Integer status;
    /** Time value. */
    private Date updateTime;
    /** lastMessagePreview field. */
    private String lastMessagePreview;
}

