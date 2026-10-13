package com.codeying.service.impl;

import com.codeying.properties.AiModelHttpProperties;
import com.codeying.service.AiModelService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** OpenAI-compatible model HTTP transport; no implicit retries or upstream response logging. */
@Service
@EnableConfigurationProperties(AiModelHttpProperties.class)
public class AiModelServiceImpl implements AiModelService {
    private final String baseUrl;
    private final String apiKey;
    private final String completionsPath;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final int failureThreshold;
    private final long coolDownNanos;

    // Guarded by this monitor; HTTP runs outside the monitor.
    private int consecutiveFailures;
    private long openedAtNanos;
    private long generation;
    private boolean open;
    private boolean recoveryInFlight;

    public AiModelServiceImpl(ObjectMapper objectMapper, AiModelHttpProperties bounds,
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.completions-path:/v1/chat/completions}") String completionsPath,
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.7}") double temperature,
            @Value("${spring.ai.openai.chat.options.max-tokens:512}") int maxTokens) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.completionsPath = completionsPath;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        if (bounds.getConnectTimeoutMs() < 1 || bounds.getReadTimeoutMs() < 1
                || bounds.getFailureThreshold() < 1 || bounds.getCoolDownMs() < 1) {
            throw new IllegalArgumentException("Model HTTP bounds must be positive");
        }
        this.failureThreshold = bounds.getFailureThreshold();
        this.coolDownNanos = TimeUnit.MILLISECONDS.toNanos(bounds.getCoolDownMs());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(bounds.getConnectTimeoutMs());
        factory.setReadTimeout(bounds.getReadTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey)) throw new IllegalStateException("Model credentials unavailable");
        long permit = acquire();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());
            Map<String, Object> body = Map.of(
                    "model", model, "temperature", temperature, "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                            Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt)));
            ResponseEntity<String> response = restTemplate.exchange(baseUrl.trim() + completionsPath.trim(),
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.getBody());
            if (root == null || !root.isObject()) throw new IllegalStateException("Invalid model envelope");
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) throw new IllegalStateException("Missing model choices");
            JsonNode content = choices.get(0).path("message").path("content");
            if (!content.isTextual() || !StringUtils.hasText(content.textValue())) {
                throw new IllegalStateException("Missing model content");
            }
            succeeded(permit);
            return content.textValue();
        } catch (Exception ex) {
            failed(permit);
            // Cause retained for internal diagnosis; callers log only the safe exception class.
            throw new IllegalStateException("Model request unavailable", ex);
        }
    }

    private synchronized long acquire() {
        if (open) {
            if (recoveryInFlight || System.nanoTime() - openedAtNanos < coolDownNanos) {
                throw new IllegalStateException("Model temporarily unavailable");
            }
            recoveryInFlight = true;
        }
        return generation;
    }

    private synchronized void succeeded(long permit) {
        if (permit != generation) return; // A stale in-flight response cannot undo newer failure protection.
        consecutiveFailures = 0;
        open = false;
        recoveryInFlight = false;
    }

    private synchronized void failed(long permit) {
        if (permit != generation) return;
        consecutiveFailures++;
        if (open || consecutiveFailures >= failureThreshold) {
            open = true;
            recoveryInFlight = false;
            openedAtNanos = System.nanoTime();
            generation++;
        }
    }
}
