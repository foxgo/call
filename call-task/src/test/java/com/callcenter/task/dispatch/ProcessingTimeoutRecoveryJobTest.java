package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessingTimeoutRecoveryJobTest {

    @Test
    void shouldRecoverExpiredProcessingIdsAndReleaseQuota() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();

        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);
        task.setStatus("RUNNING");
        when(taskRepository.loadRunningTasks()).thenReturn(List.of(task));
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(queue.recoverExpiredProcessing(eq(1001L), eq(1), any(), eq(properties.getDispatchBatchSize()), any()))
                .thenReturn(List.of(11L));

        ProcessingTimeoutRecoveryJob job = new ProcessingTimeoutRecoveryJob(
                taskRepository,
                queue,
                repository,
                limiter,
                shardingRouter,
                properties,
                metrics
        );
        job.recoverExpiredProcessing();

        verify(repository).markRecoveredForRetry(eq(new ShardKey(9L, 0, 1, "dial")), eq(1001L), eq(List.of(11L)), any());
        verify(limiter, times(1)).release(9L, 1001L);
    }
}
