package com.codeying.service.impl;

import com.codeying.properties.SkyProperties;
import com.codeying.service.WechatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Wechat Service Dev Impl.
 *
 * @author Endercloud
 */
@Service
@Profile("dev")
public class WechatServiceDevImpl implements WechatService {
    private final SkyProperties skyProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WechatServiceDevImpl(SkyProperties skyProperties) {
        this.skyProperties = skyProperties;
    }

    @Override
    public String codeToOpenid(String code) {
        if (!StringUtils.hasText(code)) throw new IllegalStateException("参数错误");
        boolean mockLogin = Boolean.TRUE.equals(skyProperties.getWechat().getMockLogin());
        if (mockLogin) {
            // dev: uni.login 的 code 每次都会变化；若 openid 由 code 拼接会导致每次重启都是新用户
            return "mock_openid_dev";
        }
        if (code.startsWith("mock_") || "test".equals(code)) {
            return "mock_openid_" + code;
        }

        // dev 默认直连微信 jscode2session，保证 openid 稳定（和 prod 行为一致）
        if (!StringUtils.hasText(skyProperties.getWechat().getAppid()) || !StringUtils.hasText(skyProperties.getWechat().getSecret())) {
            return "mock_openid_dev";
        }

        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", skyProperties.getWechat().getAppid())
                .queryParam("secret", skyProperties.getWechat().getSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build(true)
                .toUri();

        Map<String, Object> result;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            String raw = resp.getBody();
            if (!StringUtils.hasText(raw)) return "mock_openid_dev";
            result = objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (RestClientException e) {
            return "mock_openid_dev";
        } catch (Exception e) {
            return "mock_openid_dev";
        }

        if (result == null) return "mock_openid_dev";
        Object openidObj = result.get("openid");
        if (openidObj instanceof String openid && StringUtils.hasText(openid)) return openid;
        return "mock_openid_dev";
    }
}
