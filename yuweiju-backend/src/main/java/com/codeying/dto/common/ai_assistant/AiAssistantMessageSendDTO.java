package com.codeying.dto.common.ai_assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 助手发送消息请求体。
 *
 * @author Endercloud
 */
@Data
public class AiAssistantMessageSendDTO {

    /** sessionId identifier. */
    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;

    /** content field. */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 发送方类型，默认用户。 */
    private Integer senderType;
}

