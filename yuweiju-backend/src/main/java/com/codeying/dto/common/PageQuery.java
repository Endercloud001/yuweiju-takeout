package com.codeying.dto.common;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Page Query 
 *
 * @author Endercloud
 */
@Data
public class PageQuery {
    /** page field. */
    @NotNull(message = "page 不能为空")
    @Min(value = 1, message = "page 必须大于等于 1")
    private Integer page;

    /** pageSize field. */
    @NotNull(message = "pageSize 不能为空")
    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize;
}

