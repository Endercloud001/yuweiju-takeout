package com.codeying.vo.user.ai_assistant;

import lombok.Data;

import java.util.Date;

/**
 * 用户端 AI 助手会话 VO。
 *
 * @author Endercloud
 */
@Data
public class AiAssistantSessionVO {
    /** Primary key id. */
    private Long id;
    /** userId identifier. */
    private Long userId;
    /** Status value. */
    private Integer status;
    /** Time value. */
    private Date createTime;
    /** Time value. */
    private Date updateTime;
    /** Time value. */
    private Date closeTime;
}

