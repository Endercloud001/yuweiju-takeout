package com.codeying.dto.admin.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Employee DTO 
 *
 * @author Endercloud
 */
@Data
public class EmployeeDTO {
    /**  ID */
    private Long id;
    /** Display name. */
    @NotBlank(message = "username 不能为空")
    private String username;
    /** Display name. */
    @NotBlank(message = "name 不能为空")
    private String name;
    /** Phone number. */
    @NotBlank(message = "phone 不能为空")
    private String phone;
    /** sex field. */
    @NotBlank(message = "sex 不能为空")
    private String sex;
    /** idNumber field. */
    @NotBlank(message = "idNumber 不能为空")
    private String idNumber;
}

