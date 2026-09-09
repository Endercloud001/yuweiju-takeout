package com.codeying.dto.user.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login DTO 
 *
 * @author Endercloud
 */
@Data
public class LoginDTO {
    /** code field. */
    @NotBlank(message = "code 不能为空")
    private String code;
}

