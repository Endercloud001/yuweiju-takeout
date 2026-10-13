package com.codeying.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Actual outbound model bounds, without retries; one recovery request after cooldown. */
@Data
@Validated
@ConfigurationProperties(prefix = "ai-assistant.model")
public class AiModelHttpProperties {
    @Min(1)
    private int connectTimeoutMs = 3000;
    @Min(1)
    private int readTimeoutMs = 20000;
    @Min(1)
    private int failureThreshold = 3;
    @Min(1)
    private int coolDownMs = 30000;
}
