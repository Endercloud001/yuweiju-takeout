package com.codeying.dto.admin.employee;

import com.codeying.dto.common.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Employee Page Query 
 *
 * @author Endercloud
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmployeePageQuery extends PageQuery {
    /** Display name. */
    @Size(max = 50, message = "name 长度不能超过 50")
    private String name;
}

