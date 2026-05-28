package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskConcurrencyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchConcurrencyLimiterTest {

    @Test
    void shouldGrantRequestedBatchWhenAllQuotasAllowIt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("call:concurrency:global")).thenReturn(1L, 2L, 3L);
        when(valueOperations.increment("call:concurrency:tenant:9")).thenReturn(1L, 2L, 3L);
        when(valueOperations.increment("call:concurrency:task:1001")).thenReturn(1L, 2L, 3L);

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(redisTemplate, properties);

        assertEquals(3, limiter.tryAcquireBatch(9L, 1001L, 10, 3));
    }

    @Test
    void shouldPartiallyGrantWhenTaskQuotaIsTight() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("call:concurrency:global")).thenReturn(1L, 2L, 3L);
        when(valueOperations.increment("call:concurrency:tenant:9")).thenReturn(1L, 2L, 3L);
        when(valueOperations.increment("call:concurrency:task:1001")).thenReturn(4L, 5L, 6L);

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(redisTemplate, properties);

        assertEquals(2, limiter.tryAcquireBatch(9L, 1001L, 5, 3));
    }

    @Test
    void shouldReleaseBatchSlots() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(redisTemplate, properties);

        limiter.releaseBatch(9L, 1001L, 2);

        verify(valueOperations, times(2)).decrement("call:concurrency:global");
        verify(valueOperations, times(2)).decrement("call:concurrency:tenant:9");
        verify(valueOperations, times(2)).decrement("call:concurrency:task:1001");
    }
}
