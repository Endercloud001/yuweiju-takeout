package com.codeying.dto.admin.common;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 默认图片查询参数。
 *
 * @author Endercloud
 */
@Data
public class DefaultImageQueryDTO {

    /** scene  */
    private String scene;

    /** limit field. */
    @Positive(message = "limit 必须大于 0")
    private Integer limit;
}