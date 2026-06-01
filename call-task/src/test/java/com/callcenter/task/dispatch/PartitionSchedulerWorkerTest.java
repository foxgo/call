package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.mq.DialDispatchPublisher;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartitionSchedulerWorkerTest {

    @Test
    void shouldReportNoWorkWhenPartitionHasNoActiveTask() {
        Fixture fixture = new Fixture();
        when(fixture.activeTaskQueue.pollNextTaskWithMeta(7)).thenReturn(Optional.empty());

        assertFalse(fixture.worker().runPartition(7));
    }

    @Test
    void shouldReactivateTaskWithUpdatedFairScoreAfterSuccessfulDispatch() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(3);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 3)).thenReturn(3);
        when(fixture.queue.claimReady(eq(9L), eq(1001L), eq(1), eq(3), any())).thenReturn(List.of(11L, 12L, 13L));
        when(fixture.dialUnitRepository.markDialingFromReady(
                any(),
                eq(1001L),
                eq(List.of(11L, 12L, 13L)),
                any(),
                any(),
                any()
        ))
                .thenReturn(List.of(unit(11L), unit(12L), unit(13L)));

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.preloadService).preloadRunningTask(fixture.task);
        verify(fixture.activeTaskQueue).reactivate(fixture.entry.meta(), 375L);
        verify(fixture.publisher, times(3)).publish(any(CallDialUnitEntity.class));
    }

    @Test
    void shouldBlockTaskWhenNoConcurrencyQuotaIsGranted() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(3);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 3)).thenReturn(0);

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.activeTaskQueue).block(fixture.entry.meta(), TaskBlockReason.CONCURRENCY_FULL);
        verify(fixture.queue, never()).claimReady(anyLong(), anyLong(), anyInt(), anyInt(), any());
        verify(fixture.publisher, never()).publish(any());
    }

    @Test
    void shouldReleaseGrantedQuotaAndBlockEmptyWhenNoReadyUnitsClaimed() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(3);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 3)).thenReturn(3);
        when(fixture.queue.claimReady(eq(9L), eq(1001L), eq(1), eq(3), any())).thenReturn(List.of());

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.concurrencyLimiter).releaseBatch(9L, 1001L, 3);
        verify(fixture.activeTaskQueue).block(fixture.entry.meta(), TaskBlockReason.EMPTY);
        verify(fixture.dialUnitRepository, never()).markDialingFromReady(
                any(ShardKey.class),
                anyLong(),
                anyList(),
                anyString(),
                any(),
                any()
        );
    }

    @Test
    void shouldRollbackUnusedConcurrencySlotsWhenClaimedOrMarkedCountShrinks() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(4);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 4)).thenReturn(4);
        when(fixture.queue.claimReady(eq(9L), eq(1001L), eq(1), eq(4), any())).thenReturn(List.of(11L, 12L, 13L));
        when(fixture.dialUnitRepository.markDialingFromReady(
                any(),
                eq(1001L),
                eq(List.of(11L, 12L, 13L)),
                any(),
                any(),
                any()
        ))
                .thenReturn(List.of(unit(11L)));

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.concurrencyLimiter).releaseBatch(9L, 1001L, 1);
        verify(fixture.concurrencyLimiter).releaseBatch(9L, 1001L, 2);
        verify(fixture.activeTaskQueue).block(fixture.entry.meta(), TaskBlockReason.EMPTY);
        verify(fixture.publisher).publish(any(CallDialUnitEntity.class));
    }

    @Test
    void shouldReofferIdsThatFailToTransitionFromReadyToDialing() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(3);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 3)).thenReturn(3);
        when(fixture.queue.claimReady(eq(9L), eq(1001L), eq(1), eq(3), any())).thenReturn(List.of(11L, 12L, 13L));
        when(fixture.dialUnitRepository.markDialingFromReady(
                any(),
                eq(1001L),
                eq(List.of(11L, 12L, 13L)),
                any(),
                any(),
                any()
        )).thenReturn(List.of(unit(11L)));

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.queue).offerReady(
                eq(1001L),
                eq(1),
                argThat(units -> units.stream().map(CallDialUnitEntity::getId).toList().equals(List.of(12L, 13L)))
        );
    }

    private static CallDialUnitEntity unit(long id) {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(id);
        unit.setTaskId(1001L);
        unit.setTenantId(9L);
        unit.setPhone("1380013800" + id);
        return unit;
    }

    private static final class Fixture {
        private final ActiveTaskQueue activeTaskQueue = mock(ActiveTaskQueue.class);
        private final CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        private final DialUnitPreloadService preloadService = mock(DialUnitPreloadService.class);
        private final RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        private final CallDialUnitRepository dialUnitRepository = mock(CallDialUnitRepository.class);
        private final DispatchConcurrencyLimiter concurrencyLimiter = mock(DispatchConcurrencyLimiter.class);
        private final DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        private final ShardingRouter shardingRouter = mock(ShardingRouter.class);
        private final CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        private final CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        private final CallTaskEntity task = runningTask();
        private final ActiveTaskQueue.ActiveTaskEntry entry = new ActiveTaskQueue.ActiveTaskEntry(
                1001L,
                new TaskSchedulingMeta(1001L, 9L, 1, 8, 7, 0L, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE)
        );

        private Fixture() {
            when(activeTaskQueue.pollNextTaskWithMeta(7)).thenReturn(Optional.of(entry));
            when(taskRepository.findRequired(9L, 1001L)).thenReturn(task);
            when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        }

        private PartitionSchedulerWorker worker() {
            return new PartitionSchedulerWorker(
                    activeTaskQueue,
                    taskRepository,
                    preloadService,
                    queue,
                    dialUnitRepository,
                    concurrencyLimiter,
                    publisher,
                    properties,
                    shardingRouter,
                    metrics
            );
        }

        private static CallTaskEntity runningTask() {
            CallTaskEntity task = new CallTaskEntity();
            task.setId(1001L);
            task.setTenantId(9L);
            task.setStatus("RUNNING");
            task.setMaxConcurrency(20);
            return task;
        }
    }
}
