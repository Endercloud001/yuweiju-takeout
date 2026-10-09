package com.codeying.service.impl;

import com.codeying.constant.RedisKeys;
import com.codeying.properties.AnalysisProperties;
import com.codeying.vo.common.ai_assistant.AiAssistantDishCardVO;
import com.codeying.vo.common.ai_assistant.AiAssistantSendReplyVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AnalysisObservationFailureTest {
    @Test
    @SuppressWarnings("unchecked")
    void degradationLogMasksReferencesAndRetainsSafeCauseLocations() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new IllegalStateException("secret Redis body",
                new IllegalArgumentException("secret HTTP token")));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AnalysisObservationServiceImpl.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            var observation = new AnalysisObservationServiceImpl(redis, new ObjectMapper(), new AnalysisProperties());
            assertDoesNotThrow(() -> observation.recordRecommendationConversion(123456789L, 987654321L, List.of(1L)));
            String message = capture.list.get(0).getFormattedMessage();
            assertTrue(message.contains("userRef=***6789"));
            assertTrue(message.contains("orderRef=***4321"));
            assertTrue(message.contains("java.lang.IllegalStateException@"));
            assertTrue(message.contains(" <- java.lang.IllegalArgumentException@"));
            assertFalse(message.contains("123456789"));
            assertFalse(message.contains("987654321"));
            assertFalse(message.contains("secret"));
            assertTrue(capture.list.get(0).getThrowableProxy() == null);
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedContextWriteStopsMetricsAndDoesNotEscape() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("fixture write failure")).when(values).set(anyString(), anyString(), any(Duration.class));
        var observation = new AnalysisObservationServiceImpl(redis, new ObjectMapper(), new AnalysisProperties());
        var reply = new AiAssistantSendReplyVO();
        var dish = new AiAssistantDishCardVO(); dish.setDishId(1L); reply.setDishes(List.of(dish));
        assertDoesNotThrow(() -> observation.recordRecommendationExposure(1L, 1L, 1L, reply));
        verify(redis, never()).opsForSet();
        verify(values, never()).increment(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void optionalContextGetFailureDoesNotEscapeClickOrConversion() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(RedisKeys.aiRecommendContextKey(1L))).thenThrow(new IllegalStateException("fixture read failure"));
        var observation = new AnalysisObservationServiceImpl(redis, new ObjectMapper(), new AnalysisProperties());
        assertDoesNotThrow(() -> observation.recordRecommendationClick(1L, 1L));
        assertDoesNotThrow(() -> observation.recordRecommendationConversion(1L, 2L, List.of(1L)));
        verify(redis, never()).opsForSet();
        verify(values, never()).increment(anyString());
    }
}
