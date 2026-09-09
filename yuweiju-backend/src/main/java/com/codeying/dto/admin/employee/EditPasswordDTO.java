package com.codeying.dto.admin.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Edit Password DTO 
 *
 * @author Endercloud
 */
@Data
public class EditPasswordDTO {
    /** empId identifier. */
    @NotNull(message = "empId 不能为空")
    private Long empId;
    /** oldPassword field. */
    @NotBlank(message = "oldPassword 不能为空")
    private String oldPassword;
    /** newPassword field. */
    @NotBlank(message = "newPassword 不能为空")
    private String newPassword;
}

