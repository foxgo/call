package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskConcurrencyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DispatchConcurrencyLimiterTest {

    @Test
    void shouldRejectWhenTaskConcurrencyQuotaIsExceeded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("call:concurrency:global")).thenReturn(1L);
        when(valueOperations.increment("call:concurrency:tenant:9")).thenReturn(1L);
        when(valueOperations.increment("call:concurrency:task:1001")).thenReturn(6L);

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(redisTemplate, properties);

        assertFalse(limiter.tryAcquire(9L, 1001L, 5));
    }
}
