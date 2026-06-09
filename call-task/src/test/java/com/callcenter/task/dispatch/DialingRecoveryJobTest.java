package com.callcenter.task.dispatch;

import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
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

class DialingRecoveryJobTest {

    @Test
    void shouldRecoverExpiredDialingForOwnedPartitionsAndReactivateTasks() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        DispatchConcurrencyLimiter concurrencyLimiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setPartitionCount(8);
        properties.setDispatchBatchSize(20);

        when(partitionManager.ownedPartitions()).thenReturn(List.of(1));
        when(repository.findExpiredDialingBatches(any(), eq(20))).thenReturn(List.of(
                new CallDialUnitRepository.ExpiredDialingBatch(9L, 1001L, List.of(11L, 12L)),
                new CallDialUnitRepository.ExpiredDialingBatch(9L, 1002L, List.of(21L))
        ));
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.markRecoveredForRetry(eq(new ShardKey(9L, 0, 1, "dial")), eq(1001L), eq(List.of(11L, 12L)), any()))
                .thenReturn(2);

        DialingRecoveryJob job = new DialingRecoveryJob(
                partitionManager,
                repository,
                concurrencyLimiter,
                shardingRouter,
                properties,
                metrics,
                activationService
        );

        job.recoverExpiredDialing();

        verify(repository).markRecoveredForRetry(eq(new ShardKey(9L, 0, 1, "dial")), eq(1001L), eq(List.of(11L, 12L)), any());
        verify(concurrencyLimiter).releaseBatch(9L, 1001L, 2);
        verify(activationService).activate(9L, 1001L);
        verify(repository, never()).markRecoveredForRetry(any(), eq(1002L), any(), any());
        verify(activationService, never()).activate(9L, 1002L);
    }
}
