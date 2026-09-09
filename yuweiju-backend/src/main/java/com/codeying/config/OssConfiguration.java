package com.codeying.config;

import com.codeying.properties.AliOssProperties;
import com.codeying.utils.AliOssUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 配置。
 *
 * @author Endercloud
 */
@Configuration
public class OssConfiguration {

    /**
     * 构建 AliOssUtil Bean。
     *
     * @param aliOssProperties OSS 配置
     * @return AliOssUtil
     */
    @Bean
    @ConditionalOnMissingBean
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties) {
        return new AliOssUtil(
                aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName(),
                aliOssProperties.getPublicBaseUrl(),
                aliOssProperties.getPrivateBucket(),
                aliOssProperties.getSignedUrlExpireSeconds()
        );
    }
}