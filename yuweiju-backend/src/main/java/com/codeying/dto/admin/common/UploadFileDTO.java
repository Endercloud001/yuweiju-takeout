package com.codeying.dto.admin.common;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload File DTO 
 *
 * @author Endercloud
 */
@Data
public class UploadFileDTO {
    /** file field. */
    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    /** scene field. */
    @Size(max = 32, message = "scene 长度不能超过 32")
    private String scene;

    /** dir field. */
    @Size(max = 100, message = "dir 长度不能超过 100")
    private String dir;

    /** maxSize field. */
    @Min(value = 1, message = "maxSize 必须大于 0")
    private Long maxSize;
}

