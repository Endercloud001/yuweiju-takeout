package com.codeying.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 百度地图配置。
 *
 * @author Endercloud
 */
@Data
@Validated
@ConfigurationProperties(prefix = "sky.baidu-map")
public class BaiduMapProperties {

    @NotBlank(message = "sky.baidu-map.ak 不能为空")
    private String ak;
}

