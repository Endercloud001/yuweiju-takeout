package com.codeying.service.impl;

import com.codeying.service.AiModelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek(OpenAI 兼容协议)模型调用实现。
 *
 * @author Endercloud
 */
@Service
public class AiModelServiceImpl implements AiModelService {
/**
 * LoggerFactory.getLogger
 * @return 
 */

    private static final Logger log = LoggerFactory.getLogger(AiModelServiceImpl.class);

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.completions-path:/v1/chat/completions}")
    private String completionsPath;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:512}")
    private Integer maxTokens;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public AiModelServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DEEPSEEK_API_KEY 未配置");
        }
        String url = (baseUrl == null ? "" : baseUrl.trim()) + (completionsPath == null ? "/v1/chat/completions" : completionsPath.trim());

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                        Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!StringUtils.hasText(response.getBody())) {
            throw new IllegalStateException("模型响应为空");
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("模型返回 choices 为空");
            }
            String content = choices.get(0).path("message").path("content").asText();
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("模型 message.content 为空");
            }
            return content;
        } catch (Exception ex) {
            log.error("解析模型返回失败: {}", ex.getMessage());
            throw new IllegalStateException("解析模型返回失败");
        }
    }
}

