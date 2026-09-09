package com.codeying.dto.common.customer_service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人工客服消息列表查询（游标分页）。
 *
 * @author Endercloud
 */
@Data
public class CustomerServiceMessageListQuery {
    /** sessionId identifier. */
    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;

    /** 向前翻页游标（取 id < beforeId） */
    private Long beforeId;

    /** limit field. */
    @Min(value = 1, message = "limit 必须大于等于 1")
    @Max(value = 200, message = "limit 不能超过 200")
    private Integer limit;
}
