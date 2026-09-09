package com.codeying.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 项目统一配置（JWT、微信、支付等）。
 *
 * @author Endercloud
 */
@Data
@Validated
@ConfigurationProperties(prefix = "sky")
public class SkyProperties {

    @Valid
    private JwtProperties jwt = new JwtProperties();

    @Valid
    private WechatProperties wechat = new WechatProperties();

    @Valid
    private QWeatherProperties qweather = new QWeatherProperties();

    @Data
    public static class JwtProperties {
        @NotBlank(message = "sky.jwt.adminSecretKey 不能为空")
        private String adminSecretKey;

        @NotNull(message = "sky.jwt.adminTtl 不能为空")
        private Long adminTtl;

        @NotBlank(message = "sky.jwt.adminTokenName 不能为空")
        private String adminTokenName;

        @NotBlank(message = "sky.jwt.userSecretKey 不能为空")
        private String userSecretKey;

        @NotNull(message = "sky.jwt.userTtl 不能为空")
        private Long userTtl;

        @NotBlank(message = "sky.jwt.userTokenName 不能为空")
        private String userTokenName;
    }

    @Data
    public static class WechatProperties {
        @NotBlank(message = "sky.wechat.appid 不能为空")
        private String appid;

        @NotBlank(message = "sky.wechat.secret 不能为空")
        private String secret;

        private Boolean mockLogin;
        private String mchid;
        private String mchSerialNo;
        private String privateKeyFilePath;
        private String apiV3Key;
        private String weChatPayCertFilePath;
        private String notifyUrl;
        private String refundNotifyUrl;
    }

    @Data
    public static class QWeatherProperties {
        private String apiHost;
        private String projectId;
        private String credentialId;
        private String privateKey;
        private Boolean failFast = false;
    }
}
