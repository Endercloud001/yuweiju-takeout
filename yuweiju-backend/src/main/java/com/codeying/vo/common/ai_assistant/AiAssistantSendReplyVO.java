package com.codeying.vo.common.ai_assistant;

import lombok.Data;

import java.util.List;

/**
 * AI 助手发送消息响应体。
 *
 * @author Endercloud
 */
@Data
public class AiAssistantSendReplyVO {
    /** reply field. */
    private String reply;
    /** dishes field. */
    private List<AiAssistantDishCardVO> dishes;
    /** action field. */
    private String action;
    /** payload field. */
    private Object payload;
}

