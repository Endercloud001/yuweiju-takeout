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
 * Wechat Service Impl service implementation.
 *
 * @author Endercloud
 */
@Service
@Profile("prod")
public class WechatServiceImpl implements WechatService {
    private final SkyProperties skyProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WechatServiceImpl(SkyProperties skyProperties) {
        this.skyProperties = skyProperties;
    }

    @Override
    public String codeToOpenid(String code) {
        if (!StringUtils.hasText(code)) throw new IllegalStateException("参数错误");
        if (!StringUtils.hasText(skyProperties.getWechat().getAppid()) || !StringUtils.hasText(skyProperties.getWechat().getSecret())) {
            throw new IllegalStateException("微信登录配置缺失(appid/secret)");
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
            if (!StringUtils.hasText(raw)) throw new IllegalStateException("微信登录失败: empty response");
            result = objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (RestClientException e) {
            throw new IllegalStateException("微信登录失败: " + e.getMessage());
        } catch (Exception e) {
            throw new IllegalStateException("微信登录失败: " + e.getMessage());
        }
        if (result == null) throw new IllegalStateException("微信登录失败: empty response");
        Object openidObj = result.get("openid");
        if (!(openidObj instanceof String openid) || !StringUtils.hasText(openid)) {
            Object errcode = result.get("errcode");
            Object errmsg = result.get("errmsg");
            if (errcode != null || errmsg != null) {
                throw new IllegalStateException("微信登录失败: errcode=" + errcode + ", errmsg=" + errmsg);
            }
            throw new IllegalStateException("微信登录失败: openid missing");
        }
        return (String) openidObj;
    }
}
