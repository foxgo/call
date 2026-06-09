package com.callcenter.task.dispatch;

import com.callcenter.task.repository.entity.CallDialUnitEntity;
import com.callcenter.persistence.config.ShardProperties;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisDialUnitQueueTest {

    @Test
    void shouldOfferReadyUnitsWithReadyScores() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        RedisDialUnitQueue queue = new RedisDialUnitQueue(
                redisTemplate,
                scriptRepository,
                new CallTaskDispatchProperties(),
                newShardingRouter()
        );

        queue.offerReady(9L, 1001L, List.of(unit(11L, 1000L, 1.5F), unit(12L, 2000L, 0.0F)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<ZSetOperations.TypedTuple<String>>> tuples = ArgumentCaptor.forClass(Set.class);
        verify(zSetOps).add(eq(RedisQueueKeys.ready(1, 1001L)), tuples.capture());

        assertEquals(2, tuples.getValue().size());
        assertReadyTuple(tuples.getValue(), "11", 1001.5D);
        assertReadyTuple(tuples.getValue(), "12", 2000D);
    }

    @Test
    void shouldClaimReadyIdsAtomicallyFromReadyWindow() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<List>>any(),
                eq(List.of(RedisQueueKeys.ready(1, 1001L))),
                anyString()
        )).thenReturn(List.of("11", "12"));

        RedisDialUnitQueue queue = new RedisDialUnitQueue(
                redisTemplate,
                scriptRepository,
                new CallTaskDispatchProperties(),
                newShardingRouter()
        );

        assertEquals(List.of(11L, 12L), queue.claimReady(9L, 1001L, 2, Instant.ofEpochMilli(5000)));
    }

    @Test
    void shouldReportOnlyReadyWindowSize() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisQueueScriptRepository scriptRepository = new RedisQueueScriptRepository();
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.zCard(eq(RedisQueueKeys.ready(1, 1001L)))).thenReturn(3L);

        RedisDialUnitQueue queue = new RedisDialUnitQueue(
                redisTemplate,
                scriptRepository,
                new CallTaskDispatchProperties(),
                newShardingRouter()
        );

        assertEquals(3L, queue.windowSize(9L, 1001L));
    }

    private static ShardingRouter newShardingRouter() {
        return new ShardingRouter(new ShardProperties());
    }

    private static CallDialUnitEntity unit(long id, long nextCallTimeMillis, float score) {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(id);
        unit.setNextCallTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(nextCallTimeMillis), ZoneOffset.UTC));
        unit.setScore(score);
        return unit;
    }

    private static void assertReadyTuple(Set<ZSetOperations.TypedTuple<String>> tuples, String value, double score) {
        ZSetOperations.TypedTuple<String> tuple = tuples.stream()
                .filter(candidate -> value.equals(candidate.getValue()))
                .findFirst()
                .orElse(null);
        assertNotNull(tuple);
        assertEquals(score, tuple.getScore());
    }
}
