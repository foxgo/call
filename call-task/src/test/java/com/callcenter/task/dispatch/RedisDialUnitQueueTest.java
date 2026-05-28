package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDialUnitQueueTest {

    @Test
    void shouldMoveIdsFromReadyToProcessingAtomically() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<List>>any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        ))
                .thenReturn(List.of("1", "2"));

        RedisDialUnitQueue queue = new RedisDialUnitQueue(redisTemplate, scriptRepository, new CallTaskDispatchProperties());
        List<Long> ids = queue.claimReady(9L, 1001L, 0, 2, Instant.ofEpochMilli(5000));

        assertEquals(List.of(1L, 2L), ids);
    }

    @Test
    void shouldCalculateWindowSizeAcrossAllQueues() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        @SuppressWarnings("unchecked")
        var zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.zCard(eq(RedisQueueKeys.ready(1001L, 1)))).thenReturn(3L);
        when(zSetOps.zCard(eq(RedisQueueKeys.processing(1001L, 1)))).thenReturn(2L);
        when(zSetOps.zCard(eq(RedisQueueKeys.retry(1001L, 1)))).thenReturn(4L);

        RedisDialUnitQueue queue = new RedisDialUnitQueue(redisTemplate, scriptRepository, new CallTaskDispatchProperties());

        assertEquals(9L, queue.windowSize(1001L, 1));
    }

    @Test
    void shouldParsePoppedRetryDueItems() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<List>>any(),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(List.of("9:1001:1:11"));

        RedisDialUnitQueue queue = new RedisDialUnitQueue(redisTemplate, scriptRepository, new CallTaskDispatchProperties());

        assertEquals(List.of(new RetryDueItem(9L, 1001L, 1, 11L)), queue.popDueRetryItems(7, Instant.now(), 10));
    }

    @Test
    void shouldWriteRetryDueIndexWhenSchedulingRetry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        @SuppressWarnings("unchecked")
        var zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        RedisDialUnitQueue queue = new RedisDialUnitQueue(redisTemplate, scriptRepository, new CallTaskDispatchProperties());

        queue.scheduleRetry(9L, 1001L, 1, 11L, Instant.ofEpochMilli(5000));

        verify(zSetOps).add(RedisQueueKeys.retry(1001L, 1), "11", 5000D);
        verify(zSetOps).add(eq(RedisQueueKeys.retryDue(105)), eq("9:1001:1:11"), anyDouble());
    }
}
