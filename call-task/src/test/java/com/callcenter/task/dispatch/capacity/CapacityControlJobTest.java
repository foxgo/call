package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.repository.entity.CallTaskEntity;
import com.callcenter.task.config.CallTaskCapacityControlProperties;
import com.callcenter.task.dispatch.ActiveTaskQueue;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.dispatch.TaskBlockReason;
import com.callcenter.task.dispatch.TaskSchedulingMeta;
import com.callcenter.task.dispatch.TaskSchedulingState;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallTaskRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapacityControlJobTest {

    @Test
    void shouldAllocateTargetsAndReactivateTasksBlockedByConcurrency() {
        ActiveTaskQueue activeTaskQueue = mock(ActiveTaskQueue.class);
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        DispatchMetricsCollector collector = mock(DispatchMetricsCollector.class);
        CapacityProvider provider = mock(CapacityProvider.class);
        CapacityControlEngine engine = mock(CapacityControlEngine.class);
        TaskTargetConcurrencyRegistry registry = mock(TaskTargetConcurrencyRegistry.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskMetrics taskMetrics = new CallTaskMetrics(new SimpleMeterRegistry());
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        properties.setPoolKey("ai-default");
        TaskTargetAllocator allocator = new TaskTargetAllocator(properties);

        TaskSchedulingMeta blocked = new TaskSchedulingMeta(
                1001L, 9L, 1, 16, 3, 0L, TaskSchedulingState.BLOCKED, TaskBlockReason.CONCURRENCY_FULL
        );
        TaskSchedulingMeta active = new TaskSchedulingMeta(
                1002L, 9L, 2, 8, 3, 0L, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE
        );
        TaskSchedulingMeta paused = new TaskSchedulingMeta(
                1003L, 9L, 4, 2, 3, 0L, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE
        );

        when(activeTaskQueue.listKnownMetas()).thenReturn(List.of(blocked, active, paused));
        when(provider.snapshot()).thenReturn(new CapacitySnapshot("ai-default", 40, 10, 30, 0.25d, 0.95d, Instant.parse("2026-06-01T00:00:00Z")));
        when(taskRepository.findRequired(9L, 1001L)).thenReturn(task(1001L, 9L, "RUNNING", 30));
        when(taskRepository.findRequired(9L, 1002L)).thenReturn(task(1002L, 9L, "RUNNING", 10));
        when(taskRepository.findRequired(9L, 1003L)).thenReturn(task(1003L, 9L, "PAUSED", 10));
        when(collector.collectForTask(9L, 1001L)).thenReturn(snapshot(1001L));
        when(collector.collectForTask(9L, 1002L)).thenReturn(snapshot(1002L));
        when(engine.decide(eq(new ControlInput(snapshot(1001L), provider.snapshot(), new TaskPolicy(30, 1, 30), 12, 10)), eq(
                new TaskTargetState(10, Instant.parse("2026-06-01T00:00:00Z"), "old", Instant.parse("2026-06-01T00:00:30Z"))
        ), org.mockito.ArgumentMatchers.any())).thenReturn(new ControlDecision(20, "adjusted"));
        when(engine.decide(eq(new ControlInput(snapshot(1002L), provider.snapshot(), new TaskPolicy(10, 1, 10), 12, 8)), eq(
                new TaskTargetState(8, Instant.parse("2026-06-01T00:00:00Z"), "old", Instant.parse("2026-06-01T00:00:30Z"))
        ), org.mockito.ArgumentMatchers.any())).thenReturn(new ControlDecision(8, "adjusted"));
        when(registry.loadTaskTarget(9L, 1001L)).thenReturn(Optional.of(
                new TaskTargetState(10, Instant.parse("2026-06-01T00:00:00Z"), "old", Instant.parse("2026-06-01T00:00:30Z"))
        ));
        when(registry.loadTaskTarget(9L, 1002L)).thenReturn(Optional.of(
                new TaskTargetState(8, Instant.parse("2026-06-01T00:00:00Z"), "old", Instant.parse("2026-06-01T00:00:30Z"))
        ));

        CapacityControlJob job = new CapacityControlJob(
                activeTaskQueue,
                taskRepository,
                collector,
                provider,
                engine,
                allocator,
                registry,
                activationService,
                taskMetrics,
                properties
        );

        job.recalculateTargets();

        verify(registry).savePoolTarget("ai-default", 40);
        verify(registry).saveTaskTarget(eq(9L), eq(1001L), argThat(state -> state.targetConcurrency() == 20));
        verify(registry).saveTaskTarget(eq(9L), eq(1002L), argThat(state -> state.targetConcurrency() == 8));
        verify(activationService).activate(9L, 1001L);
        verify(activationService, never()).activate(9L, 1002L);
        verify(registry, never()).saveTaskTarget(eq(9L), eq(1003L), org.mockito.ArgumentMatchers.any());
    }

    private static CallTaskEntity task(Long taskId, Long tenantId, String status, int maxConcurrency) {
        CallTaskEntity task = new CallTaskEntity();
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setStatus(status);
        task.setMaxConcurrency(maxConcurrency);
        return task;
    }

    private static DispatchMetricsSnapshot snapshot(Long taskId) {
        return new DispatchMetricsSnapshot(taskId, 0.12d, 0.60d, 0.25d, 0.95d, 0.25d, 12L, 20L, 80L, Instant.parse("2026-06-01T00:00:00Z"));
    }
}
