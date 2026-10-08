package com.codeying.service.impl;

import com.codeying.constant.RedisKeys;
import com.codeying.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Issue19ShopStatusTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ShopStatusServiceImpl service = new ShopStatusServiceImpl(redis);

    @Test void retainsMissingKeyAndNonzeroNormalization() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(RedisKeys.shopStatusKey())).thenReturn(null, "", "0", "1", "2");
        assertEquals(1, service.getStatus()); assertEquals(1, service.getStatus());
        assertEquals(0, service.getStatus()); assertEquals(1, service.getStatus()); assertEquals(1, service.getStatus());
        service.setStatus(0); service.setStatus(2);
        verify(values).set(RedisKeys.shopStatusKey(), "0"); verify(values).set(RedisKeys.shopStatusKey(), "1");
    }

    @Test void readAndWriteCacheFailuresAreVisibleAndKeepCause() {
        when(redis.opsForValue()).thenReturn(values);
        var failure = new RedisConnectionFailureException("isolated unavailable");
        when(values.get(anyString())).thenThrow(failure);
        doThrow(failure).when(values).set(anyString(), anyString());
        assertSame(failure, assertThrows(BusinessException.class, service::getStatus).getCause());
        assertSame(failure, assertThrows(BusinessException.class, () -> service.setStatus(0)).getCause());
    }

    @Test void devStatusStillStaysOpen() {
        var dev = new ShopStatusServiceDevImpl(); dev.setStatus(0); assertEquals(1, dev.getStatus());
    }
}
