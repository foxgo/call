package com.callcenter.task.dispatch;

import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessingTimeoutRecoveryJobTest {

    @Test
    void shouldRecoverExpiredProcessingIdsReleaseRecoveredQuotaAndReactivateTask() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();

        when(partitionManager.ownedPartitions()).thenReturn(List.of(7));
        when(queue.popExpiredProcessingItems(eq(7), any(), eq(properties.getDispatchBatchSize())))
                .thenReturn(List.of(new ProcessingTimeoutItem(9L, 1001L, 1, 11L)));
        when(queue.recoverExpiredProcessingUnit(1001L, 1, 11L, 0D)).thenReturn(true);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.markRecoveredForRetry(eq(new ShardKey(9L, 0, 1, "dial")), eq(1001L), eq(List.of(11L)), any()))
                .thenReturn(1);

        ProcessingTimeoutRecoveryJob job = new ProcessingTimeoutRecoveryJob(
                partitionManager,
                queue,
                repository,
                limiter,
                shardingRouter,
                properties,
                metrics,
                activationService
        );
        job.recoverExpiredProcessing();

        verify(queue).popExpiredProcessingItems(eq(7), any(), eq(properties.getDispatchBatchSize()));
        verify(queue).recoverExpiredProcessingUnit(1001L, 1, 11L, 0D);
        verify(repository).markRecoveredForRetry(eq(new ShardKey(9L, 0, 1, "dial")), eq(1001L), eq(List.of(11L)), any());
        verify(limiter).releaseBatch(9L, 1001L, 1);
        verify(activationService).activate(9L, 1001L);
    }

    @Test
    void shouldNotReleaseOrReactivateWhenRecoveryDidNotChangeAnyRow() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();

        when(partitionManager.ownedPartitions()).thenReturn(List.of(7));
        when(queue.popExpiredProcessingItems(eq(7), any(), eq(properties.getDispatchBatchSize())))
                .thenReturn(List.of(new ProcessingTimeoutItem(9L, 1001L, 1, 11L)));
        when(queue.recoverExpiredProcessingUnit(1001L, 1, 11L, 0D)).thenReturn(true);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.markRecoveredForRetry(eq(new ShardKey(9L, 0, 1, "dial")), eq(1001L), eq(List.of(11L)), any()))
                .thenReturn(0);

        ProcessingTimeoutRecoveryJob job = new ProcessingTimeoutRecoveryJob(
                partitionManager,
                queue,
                repository,
                limiter,
                shardingRouter,
                properties,
                metrics,
                activationService
        );
        job.recoverExpiredProcessing();

        verify(limiter, never()).releaseBatch(9L, 1001L, 0);
        verify(activationService, never()).activate(9L, 1001L);
    }
}
