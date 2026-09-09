package com.codeying.dto.admin.customer_service;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人工客服会话关闭请求体。
 *
 * @author Endercloud
 */
@Data
public class CustomerServiceCloseBody {
    /** sessionId identifier. */
    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;
    /** 关闭原因 */
    private String closeReason;
}
