package com.codeying.dto.user.common;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Id DTO 
 *
 * @author Endercloud
 */
@Data
public class IdDTO {
    /** Primary key id. */
    @NotNull(message = "id 不能为空")
    private Long id;
}

