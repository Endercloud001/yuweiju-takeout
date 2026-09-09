package com.codeying.dto.admin.category;

import com.codeying.dto.common.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Category Page Query 
 *
 * @author Endercloud
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryPageQuery extends PageQuery {
    /** Display name. */
    @Size(max = 50, message = "名称长度不能超过 50")
    private String name;
    /** type  */
    private Integer type;
}

