package com.codeying.dto.admin.category;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Category DTO 
 *
 * @author Endercloud
 */
@Data
public class CategoryDTO {
    /**  ID */
    private Long id;
    /** Display name. */
    @NotBlank(message = "分类名称不能为空")
    private String name;
    /** type field. */
    @NotNull(message = "分类类型不能为空")
    @Min(value = 1, message = "分类类型取值 1 或 2")
    @Max(value = 2, message = "分类类型取值 1 或 2")
    private Integer type;
    /** sort field. */
    @NotNull(message = "排序不能为空")
    private Integer sort;
    /**  */
    private Integer status;
}

