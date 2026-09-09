package com.codeying.dto.admin.dish;

import com.codeying.dto.common.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Dish Page Query 
 *
 * @author Endercloud
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DishPageQuery extends PageQuery {
    /** Display name. */
    @Size(max = 50, message = "名称长度不能超过 50")
    private String name;

    /** categoryId identifier. */
    @Min(value = 1, message = "categoryId 必须大于等于 1")
    private Long categoryId;

    /** Status value. */
    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;
}

