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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartitionSchedulerWorkerTest {

    @Test
    void shouldDispatchOwnedPartitionTaskWithSmallBudget() {
        ActiveTaskQueue activeTaskQueue = mock(ActiveTaskQueue.class);
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        DialUnitPreloadService preloadService = mock(DialUnitPreloadService.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallDialUnitRepository dialUnitRepository = mock(CallDialUnitRepository.class);
        DispatchConcurrencyLimiter concurrencyLimiter = mock(DispatchConcurrencyLimiter.class);
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setDispatchBatchSize(10);

        when(activeTaskQueue.pollNextTask(7)).thenReturn(Optional.of(1001L));
        when(activeTaskQueue.loadMeta(1001L)).thenReturn(Optional.of(
                new TaskSchedulingMeta(1001L, 9L, 1, 8, 7, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE)
        ));

        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);
        task.setStatus("RUNNING");
        task.setMaxConcurrency(20);
        when(taskRepository.findRequired(9L, 1001L)).thenReturn(task);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(concurrencyLimiter.available(9L, 1001L, 20)).thenReturn(3);
        when(queue.claimReady(eq(9L), eq(1001L), eq(1), eq(3), any())).thenReturn(List.of(11L, 12L, 13L));

        CallDialUnitEntity unit1 = new CallDialUnitEntity();
        unit1.setId(11L);
        unit1.setTaskId(1001L);
        unit1.setTenantId(9L);
        unit1.setPhone("13800138000");
        CallDialUnitEntity unit2 = new CallDialUnitEntity();
        unit2.setId(12L);
        unit2.setTaskId(1001L);
        unit2.setTenantId(9L);
        unit2.setPhone("13800138001");
        CallDialUnitEntity unit3 = new CallDialUnitEntity();
        unit3.setId(13L);
        unit3.setTaskId(1001L);
        unit3.setTenantId(9L);
        unit3.setPhone("13800138002");
        when(dialUnitRepository.markDialing(any(), eq(1001L), eq(List.of(11L, 12L, 13L)), any(), any(), any()))
                .thenReturn(List.of(unit1, unit2, unit3));

        PartitionSchedulerWorker worker = new PartitionSchedulerWorker(
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

        worker.runPartition(7);

        verify(preloadService).preloadRunningTask(task);
        verify(publisher, times(3)).publish(any(CallDialUnitEntity.class));
    }
}
