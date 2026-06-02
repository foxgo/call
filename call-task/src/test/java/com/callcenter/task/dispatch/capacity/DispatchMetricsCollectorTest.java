package com.callcenter.task.dispatch.capacity;

import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskCapacityControlProperties;
import com.callcenter.task.dispatch.DispatchConcurrencyLimiter;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DispatchMetricsCollectorTest {

    @Test
    void shouldCollectTaskMetricsFromCurrentSignals() {
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        TaskTargetConcurrencyRegistry registry = mock(TaskTargetConcurrencyRegistry.class);
        CapacityProvider provider = mock(CapacityProvider.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = new CallTaskMetrics(new SimpleMeterRegistry());
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        properties.setEwmaAlpha(0.25d);

        when(limiter.currentTaskInFlight(9L, 1001L)).thenReturn(12);
        when(registry.loadTaskTarget(9L, 1001L)).thenReturn(java.util.Optional.of(
                new TaskTargetState(20, Instant.parse("2026-06-01T00:00:00Z"), "steady", Instant.parse("2026-06-01T00:00:30Z"))
        ));
        when(provider.snapshot()).thenReturn(new CapacitySnapshot(
                "ai-default",
                1000,
                250,
                750,
                0.25d,
                0.93d,
                Instant.parse("2026-06-01T00:00:00Z")
        ));
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.countRemainingDialUnits(new ShardKey(9L, 0, 1, "dial"), 1001L)).thenReturn(88L);

        metrics.incrementWritebackSuccess(1001L);
        metrics.incrementWritebackFailure(1001L);

        DispatchMetricsCollector collector = new DispatchMetricsCollector(
                limiter,
                registry,
                provider,
                repository,
                shardingRouter,
                metrics,
                properties
        );

        DispatchMetricsSnapshot snapshot = collector.collectForTask(9L, 1001L);

        assertEquals(1001L, snapshot.taskId());
        assertEquals(0.5d, snapshot.connectRate(), 0.0001d);
        assertEquals(0.6d, snapshot.occupancy(), 0.0001d);
        assertEquals(0.25d, snapshot.poolUtilization(), 0.0001d);
        assertEquals(0.93d, snapshot.trunkHealth(), 0.0001d);
        assertEquals(0.25d, snapshot.llmLoad(), 0.0001d);
        assertEquals(12L, snapshot.activeCalls());
        assertEquals(2L, snapshot.completedCalls());
        assertEquals(88L, snapshot.remainingCalls());
    }

    @Test
    void shouldSmoothConnectRateWithEwmaAcrossCollections() {
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        TaskTargetConcurrencyRegistry registry = mock(TaskTargetConcurrencyRegistry.class);
        CapacityProvider provider = mock(CapacityProvider.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = new CallTaskMetrics(new SimpleMeterRegistry());
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        properties.setEwmaAlpha(0.25d);

        when(limiter.currentTaskInFlight(9L, 1001L)).thenReturn(4);
        when(registry.loadTaskTarget(9L, 1001L)).thenReturn(java.util.Optional.of(
                new TaskTargetState(10, Instant.parse("2026-06-01T00:00:00Z"), "steady", Instant.parse("2026-06-01T00:00:30Z"))
        ));
        when(provider.snapshot()).thenReturn(new CapacitySnapshot(
                "ai-default",
                1000,
                100,
                900,
                0.10d,
                0.95d,
                Instant.parse("2026-06-01T00:00:00Z")
        ));
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.countRemainingDialUnits(new ShardKey(9L, 0, 1, "dial"), 1001L)).thenReturn(50L);

        DispatchMetricsCollector collector = new DispatchMetricsCollector(
                limiter,
                registry,
                provider,
                repository,
                shardingRouter,
                metrics,
                properties
        );

        metrics.incrementWritebackSuccess(1001L);
        metrics.incrementWritebackFailure(1001L);
        DispatchMetricsSnapshot first = collector.collectForTask(9L, 1001L);

        metrics.incrementWritebackSuccess(1001L);
        DispatchMetricsSnapshot second = collector.collectForTask(9L, 1001L);

        assertEquals(0.5d, first.connectRate(), 0.0001d);
        assertTrue(second.connectRate() > 0.5d);
        assertTrue(second.connectRate() < (2.0d / 3.0d));
    }
}
