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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTaskDispatcherTest {

    @Test
    void shouldClaimQueuedUnitsMarkDialingAndPublishMessages() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        DialUnitPreloadService preloadService = mock(DialUnitPreloadService.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setDispatchBatchSize(2);

        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);
        task.setStatus("RUNNING");
        task.setMaxConcurrency(5);
        task.setNextDispatchTime(LocalDateTime.now().minusSeconds(1));
        when(taskRepository.loadRunningTasks()).thenReturn(List.of(task));
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(queue.claimReady(eq(1001L), eq(1), eq(2), any())).thenReturn(List.of(11L, 12L));
        when(limiter.tryAcquire(9L, 1001L, 5)).thenReturn(true, true);

        CallDialUnitEntity unit1 = new CallDialUnitEntity();
        unit1.setId(11L);
        unit1.setTenantId(9L);
        unit1.setTaskId(1001L);
        unit1.setPhone("13800138000");
        CallDialUnitEntity unit2 = new CallDialUnitEntity();
        unit2.setId(12L);
        unit2.setTenantId(9L);
        unit2.setTaskId(1001L);
        unit2.setPhone("13800138001");
        when(repository.markDialing(any(), eq(1001L), eq(List.of(11L, 12L)), any(), any(), any()))
                .thenReturn(List.of(unit1, unit2));

        CallTaskDispatcher dispatcher = new CallTaskDispatcher(
                taskRepository,
                preloadService,
                queue,
                repository,
                limiter,
                publisher,
                properties,
                shardingRouter,
                metrics
        );

        dispatcher.dispatchRunningTasks();

        verify(preloadService).preloadRunningTask(task);
        verify(publisher).publish(unit1);
        verify(publisher).publish(unit2);
    }
}
