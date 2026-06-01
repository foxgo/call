package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskConcurrencyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchConcurrencyLimiterTest {

    @Test
    void shouldGrantRequestedBatchWhenAllQuotasAllowIt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(
                        "call:concurrency:global",
                        "call:concurrency:tenant:9",
                        "call:concurrency:task:1001"
                )),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(3L);

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(redisTemplate, properties);

        assertEquals(3, limiter.tryAcquireBatch(9L, 1001L, 10, 3));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shouldPartiallyGrantWhenTaskQuotaIsTight() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(
                        "call:concurrency:global",
                        "call:concurrency:tenant:9",
                        "call:concurrency:task:1001"
                )),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(2L);

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

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(
                        "call:concurrency:global",
                        "call:concurrency:tenant:9",
                        "call:concurrency:task:1001"
                )),
                org.mockito.ArgumentMatchers.eq("2"),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(valueOperations, never()).decrement("call:concurrency:global");
    }
}
