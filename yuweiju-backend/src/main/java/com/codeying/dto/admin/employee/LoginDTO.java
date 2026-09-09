package com.codeying.dto.admin.employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login DTO 
 *
 * @author Endercloud
 */
@Data
public class LoginDTO {
    /** Display name. */
    @NotBlank(message = "用户名不能为空")
    private String username;
    /** password field. */
    @NotBlank(message = "密码不能为空")
    private String password;
}

