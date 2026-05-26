package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallTaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryQueueSchedulerTest {

    @Test
    void shouldMoveDueRetryIdsBackToReadyQueue() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setDispatchBatchSize(20);

        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);
        task.setStatus("RUNNING");
        when(taskRepository.loadRunningTasks()).thenReturn(List.of(task));
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));

        RetryQueueScheduler scheduler = new RetryQueueScheduler(taskRepository, queue, shardingRouter, properties, metrics);
        scheduler.requeueDueRetries();

        verify(queue).requeueDueRetry(eq(1001L), eq(1), any(), eq(20), any());
    }
}
