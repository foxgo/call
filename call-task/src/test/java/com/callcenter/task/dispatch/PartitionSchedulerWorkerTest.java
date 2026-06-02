package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.caller.AttemptStage;
import com.callcenter.task.caller.CallerIdCandidate;
import com.callcenter.task.caller.CallerIdCandidateService;
import com.callcenter.task.caller.CallerIdSelection;
import com.callcenter.task.caller.CallerIdSelector;
import com.callcenter.task.caller.TaskCallerIdPolicy;
import com.callcenter.task.caller.TaskCallerIdPolicyService;
import com.callcenter.task.config.CallTaskDispatchProperties;
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
        when(fixture.dialUnitRepository.listByTaskIdAndIds(any(), eq(1001L), eq(List.of(11L, 12L, 13L))))
                .thenReturn(List.of(unit(11L), unit(12L), unit(13L)));
        when(fixture.candidateService.listCandidates(eq(9L), eq(1001L), any(), any()))
                .thenReturn(List.of(candidate(3001L, "02166668888")));
        when(fixture.selector.select(eq(9L), any(), any(), any()))
                .thenReturn(Optional.of(selection(3001L, "02166668888")));
        when(fixture.dialUnitRepository.markDialingSelectionsFromReady(
                any(),
                eq(1001L),
                anyList(),
                any(),
                any()
        ))
                .thenReturn(List.of(unit(11L), unit(12L), unit(13L)));

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.preloadService).preloadRunningTask(fixture.task);
        verify(fixture.activeTaskQueue).reactivate(fixture.entry.meta(), 375L);
        verify(fixture.asyncDialDispatchService, times(3)).submit(eq(new ShardKey(9L, 0, 1, "dial")), any(CallDialUnitEntity.class));
    }

    @Test
    void shouldBlockTaskWhenNoConcurrencyQuotaIsGranted() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(3);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 3)).thenReturn(0);

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.activeTaskQueue).block(fixture.entry.meta(), TaskBlockReason.CONCURRENCY_FULL);
        verify(fixture.queue, never()).claimReady(anyLong(), anyLong(), anyInt(), anyInt(), any());
        verify(fixture.asyncDialDispatchService, never()).submit(any(), any());
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
        verify(fixture.dialUnitRepository, never()).markDialingSelectionsFromReady(
                any(ShardKey.class),
                anyLong(),
                anyList(),
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
        when(fixture.dialUnitRepository.listByTaskIdAndIds(any(), eq(1001L), eq(List.of(11L, 12L, 13L))))
                .thenReturn(List.of(unit(11L), unit(12L), unit(13L)));
        when(fixture.candidateService.listCandidates(eq(9L), eq(1001L), any(), any()))
                .thenReturn(List.of(candidate(3001L, "02166668888")));
        when(fixture.selector.select(eq(9L), any(), any(), any()))
                .thenReturn(Optional.of(selection(3001L, "02166668888")));
        when(fixture.dialUnitRepository.markDialingSelectionsFromReady(
                any(),
                eq(1001L),
                anyList(),
                any(),
                any()
        ))
                .thenReturn(List.of(unit(11L)));

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.concurrencyLimiter).releaseBatch(9L, 1001L, 1);
        verify(fixture.concurrencyLimiter).releaseBatch(9L, 1001L, 2);
        verify(fixture.activeTaskQueue).block(fixture.entry.meta(), TaskBlockReason.EMPTY);
        verify(fixture.asyncDialDispatchService).submit(eq(new ShardKey(9L, 0, 1, "dial")), any(CallDialUnitEntity.class));
    }

    @Test
    void shouldReofferIdsThatFailToTransitionFromReadyToDialing() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(3);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 3)).thenReturn(3);
        when(fixture.queue.claimReady(eq(9L), eq(1001L), eq(1), eq(3), any())).thenReturn(List.of(11L, 12L, 13L));
        when(fixture.dialUnitRepository.listByTaskIdAndIds(any(), eq(1001L), eq(List.of(11L, 12L, 13L))))
                .thenReturn(List.of(unit(11L), unit(12L), unit(13L)));
        when(fixture.candidateService.listCandidates(eq(9L), eq(1001L), any(), any()))
                .thenReturn(List.of(candidate(3001L, "02166668888")));
        when(fixture.selector.select(eq(9L), any(), any(), any()))
                .thenReturn(Optional.of(selection(3001L, "02166668888")));
        when(fixture.dialUnitRepository.markDialingSelectionsFromReady(
                any(),
                eq(1001L),
                anyList(),
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

    @Test
    void shouldReofferReadyUnitsWhenNoCallerCandidateCanBeSelected() {
        Fixture fixture = new Fixture();
        fixture.properties.setDispatchBatchSize(2);
        when(fixture.concurrencyLimiter.tryAcquireBatch(9L, 1001L, 20, 2)).thenReturn(2);
        when(fixture.queue.claimReady(eq(9L), eq(1001L), eq(1), eq(2), any())).thenReturn(List.of(11L, 12L));
        when(fixture.dialUnitRepository.listByTaskIdAndIds(any(), eq(1001L), eq(List.of(11L, 12L))))
                .thenReturn(List.of(unit(11L), unit(12L)));
        when(fixture.candidateService.listCandidates(eq(9L), eq(1001L), any(), any())).thenReturn(List.of());
        when(fixture.selector.select(eq(9L), any(), any(), any())).thenReturn(Optional.empty());

        assertTrue(fixture.worker().runPartition(7));

        verify(fixture.queue).offerReady(
                eq(1001L),
                eq(1),
                argThat(units -> units.stream().map(CallDialUnitEntity::getId).toList().equals(List.of(11L, 12L)))
        );
        verify(fixture.asyncDialDispatchService, never()).submit(any(), any());
    }

    private static CallDialUnitEntity unit(long id) {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(id);
        unit.setTaskId(1001L);
        unit.setTenantId(9L);
        unit.setPhone("1380013800" + id);
        return unit;
    }

    private static CallerIdCandidate candidate(long callerIdId, String callerId) {
        return new CallerIdCandidate(callerIdId, callerId, "SHARED", 0D, 1D, 0);
    }

    private static CallerIdSelection selection(long callerIdId, String callerId) {
        return new CallerIdSelection(callerIdId, callerId, AttemptStage.FIRST_ATTEMPT, 98D, "answerRate=0.80");
    }

    private static final class Fixture {
        private final ActiveTaskQueue activeTaskQueue = mock(ActiveTaskQueue.class);
        private final CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        private final DialUnitPreloadService preloadService = mock(DialUnitPreloadService.class);
        private final RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        private final CallDialUnitRepository dialUnitRepository = mock(CallDialUnitRepository.class);
        private final DispatchConcurrencyLimiter concurrencyLimiter = mock(DispatchConcurrencyLimiter.class);
        private final AsyncDialDispatchService asyncDialDispatchService = mock(AsyncDialDispatchService.class);
        private final TaskCallerIdPolicyService policyService = mock(TaskCallerIdPolicyService.class);
        private final CallerIdCandidateService candidateService = mock(CallerIdCandidateService.class);
        private final CallerIdSelector selector = mock(CallerIdSelector.class);
        private final ShardingRouter shardingRouter = mock(ShardingRouter.class);
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
            when(policyService.toPolicy(task)).thenReturn(new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0D, 0D, 0D, false, 3600, 200));
        }

        private PartitionSchedulerWorker worker() {
            return new PartitionSchedulerWorker(
                    activeTaskQueue,
                    taskRepository,
                    preloadService,
                    queue,
                    dialUnitRepository,
                    concurrencyLimiter,
                    asyncDialDispatchService,
                    policyService,
                    candidateService,
                    selector,
                    properties,
                    shardingRouter
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
