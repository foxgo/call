package com.callcenter.task.dispatch;

import com.callcenter.persistence.config.ShardProperties;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.task.config.CallTaskConcurrencyProperties;
import com.callcenter.task.config.CallTaskCapacityControlProperties;
import com.callcenter.task.dispatch.capacity.TaskTargetConcurrencyRegistry;
import com.callcenter.task.dispatch.capacity.TaskTargetState;
import com.callcenter.task.metrics.CallTaskMetrics;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchConcurrencyLimiterTest {

    @Test
    void shouldGrantRequestedBatchWhenPoolAndTaskTargetsAllowIt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TaskTargetConcurrencyRegistry registry = mock(TaskTargetConcurrencyRegistry.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(
                        "call:concurrency:global",
                        "call:capacity:pool:ai-default:busy",
                        "call:concurrency:tenant:9",
                        "call:concurrency:task:1:1001"
                )),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(3L);
        when(registry.loadPoolTarget("ai-default")).thenReturn(Optional.of(8));
        when(registry.loadTaskTarget(9L, 1001L)).thenReturn(Optional.of(
                new TaskTargetState(6, Instant.parse("2026-06-01T00:00:00Z"), "steady", Instant.parse("2026-06-01T00:00:30Z"))
        ));

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        CallTaskCapacityControlProperties capacityProperties = new CallTaskCapacityControlProperties();
        CallTaskMetrics metrics = new CallTaskMetrics(new SimpleMeterRegistry());
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(
                redisTemplate,
                properties,
                capacityProperties,
                registry,
                metrics,
                newShardingRouter()
        );

        assertEquals(3, limiter.tryAcquireBatch(9L, 1001L, 10, 3));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shouldPartiallyGrantWhenDynamicPoolTargetIsTight() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TaskTargetConcurrencyRegistry registry = mock(TaskTargetConcurrencyRegistry.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(
                        "call:concurrency:global",
                        "call:capacity:pool:ai-default:busy",
                        "call:concurrency:tenant:9",
                        "call:concurrency:task:1:1001"
                )),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(2L);
        when(registry.loadPoolTarget("ai-default")).thenReturn(Optional.of(2));
        when(registry.loadTaskTarget(9L, 1001L)).thenReturn(Optional.of(
                new TaskTargetState(5, Instant.parse("2026-06-01T00:00:00Z"), "steady", Instant.parse("2026-06-01T00:00:30Z"))
        ));

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        CallTaskCapacityControlProperties capacityProperties = new CallTaskCapacityControlProperties();
        CallTaskMetrics metrics = new CallTaskMetrics(new SimpleMeterRegistry());
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(
                redisTemplate,
                properties,
                capacityProperties,
                registry,
                metrics,
                newShardingRouter()
        );

        assertEquals(2, limiter.tryAcquireBatch(9L, 1001L, 5, 3));
    }

    @Test
    void shouldReleaseBatchSlots() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TaskTargetConcurrencyRegistry registry = mock(TaskTargetConcurrencyRegistry.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        CallTaskCapacityControlProperties capacityProperties = new CallTaskCapacityControlProperties();
        CallTaskMetrics metrics = new CallTaskMetrics(new SimpleMeterRegistry());
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(
                redisTemplate,
                properties,
                capacityProperties,
                registry,
                metrics,
                newShardingRouter()
        );

        limiter.releaseBatch(9L, 1001L, 2);

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(
                        "call:concurrency:global",
                        "call:capacity:pool:ai-default:busy",
                        "call:concurrency:tenant:9",
                        "call:concurrency:task:1:1001"
                )),
                org.mockito.ArgumentMatchers.eq("2"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(valueOperations, never()).decrement("call:concurrency:global");
    }

    @Test
    void shouldRecordPoolRejectReasonWhenPoolTargetIsExhausted() {
        assertRejectReason(
                "call.task.capacity.limit.pool.rejected",
                100,
                2,
                2,
                1,
                1,
                1,
                5,
                10
        );
    }

    @Test
    void shouldRecordGlobalRejectReasonWhenGlobalLimitIsExhausted() {
        assertRejectReason(
                "call.task.capacity.limit.global.rejected",
                100,
                100,
                1,
                10000,
                1,
                1,
                5,
                10
        );
    }

    @Test
    void shouldRecordTenantRejectReasonWhenTenantLimitIsExhausted() {
        assertRejectReason(
                "call.task.capacity.limit.tenant.rejected",
                1,
                100,
                1,
                1,
                1,
                100,
                5,
                10
        );
    }

    @Test
    void shouldRecordTaskStaticRejectReasonWhenTaskStaticLimitIsExhausted() {
        assertRejectReason(
                "call.task.capacity.limit.task_static.rejected",
                100,
                100,
                1,
                1,
                5,
                1,
                10,
                5
        );
    }

    @Test
    void shouldRecordTaskTargetRejectReasonWhenTaskTargetLimitIsExhausted() {
        assertRejectReason(
                "call.task.capacity.limit.task_target.rejected",
                100,
                100,
                1,
                1,
                5,
                1,
                5,
                10
        );
    }

    private static void assertRejectReason(
            String metricName,
            int tenantDefaultMax,
            int poolTarget,
            int currentPool,
            int currentGlobal,
            int currentTask,
            int currentTenant,
            int taskTarget,
            int taskMaxConcurrency
    ) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TaskTargetConcurrencyRegistry registry = mock(TaskTargetConcurrencyRegistry.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("call:concurrency:global")).thenReturn(String.valueOf(currentGlobal));
        when(valueOperations.get("call:capacity:pool:ai-default:busy")).thenReturn(String.valueOf(currentPool));
        when(valueOperations.get("call:concurrency:tenant:9")).thenReturn(String.valueOf(currentTenant));
        when(valueOperations.get("call:concurrency:task:1:1001")).thenReturn(String.valueOf(currentTask));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(0L);
        when(registry.loadPoolTarget("ai-default")).thenReturn(Optional.of(poolTarget));
        when(registry.loadTaskTarget(9L, 1001L)).thenReturn(Optional.of(
                new TaskTargetState(taskTarget, Instant.parse("2026-06-01T00:00:00Z"), "steady", Instant.parse("2026-06-01T00:00:30Z"))
        ));

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CallTaskConcurrencyProperties properties = new CallTaskConcurrencyProperties();
        properties.setTenantDefaultMax(tenantDefaultMax);
        CallTaskCapacityControlProperties capacityProperties = new CallTaskCapacityControlProperties();
        capacityProperties.setPoolHardMax(1000);
        DispatchConcurrencyLimiter limiter = new DispatchConcurrencyLimiter(
                redisTemplate,
                properties,
                capacityProperties,
                registry,
                new CallTaskMetrics(meterRegistry),
                newShardingRouter()
        );

        assertEquals(0, limiter.tryAcquireBatch(9L, 1001L, taskMaxConcurrency, 1));
        assertNotNull(meterRegistry.find(metricName).counter());
        assertEquals(1.0d, meterRegistry.find(metricName).counter().count());
    }

    private static ShardingRouter newShardingRouter() {
        return new ShardingRouter(new ShardProperties());
    }
}
