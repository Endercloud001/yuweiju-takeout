package com.codeying.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 门店配置。
 *
 * @author Endercloud
 */
@Data
@Validated
@ConfigurationProperties(prefix = "sky.shop")
public class ShopProperties {

    @NotBlank(message = "sky.shop.address 不能为空")
    private String address;
}

