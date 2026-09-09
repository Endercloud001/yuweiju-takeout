package com.codeying.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 阿里 OSS 配置。
 *
 * @author Endercloud
 */
@Data
@Validated
@ConfigurationProperties(prefix = "sky.alioss")
public class AliOssProperties {

    @NotBlank(message = "sky.alioss.endpoint 不能为空")
    private String endpoint;

    @NotBlank(message = "sky.alioss.accessKeyId 不能为空")
    private String accessKeyId;

    @NotBlank(message = "sky.alioss.accessKeySecret 不能为空")
    private String accessKeySecret;

    @NotBlank(message = "sky.alioss.bucketName 不能为空")
    private String bucketName;

    private String defaultImagePrefix = "";

    private String dishDefaultImagePrefix = "";

    private String setmealDefaultImagePrefix = "";

    private String publicBaseUrl = "";

    @Positive(message = "sky.alioss.maxListCount 必须大于 0")
    private Integer maxListCount = 100;
/**
 * List.of
 * @return 
 */

    @NotEmpty(message = "sky.alioss.allowedExtensions 不能为空")
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp");

    private Boolean privateBucket = false;

    @Positive(message = "sky.alioss.signedUrlExpireSeconds 必须大于 0")
    private Integer signedUrlExpireSeconds = 300;
}