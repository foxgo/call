package com.callcenter.task.dispatch;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDialUnitQueueTest {

    @Test
    void shouldMoveIdsFromReadyToProcessingAtomically() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<List>>any(), anyList(), anyString(), anyString()))
                .thenReturn(List.of("1", "2"));

        RedisDialUnitQueue queue = new RedisDialUnitQueue(redisTemplate, scriptRepository);
        List<Long> ids = queue.claimReady(1001L, 0, 2, Instant.ofEpochMilli(5000));

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

        RedisDialUnitQueue queue = new RedisDialUnitQueue(redisTemplate, scriptRepository);

        assertEquals(9L, queue.windowSize(1001L, 1));
    }
}
