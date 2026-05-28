package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryQueueSchedulerTest {

    @Test
    void shouldMoveDueRetryIdsBackToReadyQueueAndReactivateTask() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setDispatchBatchSize(20);

        when(partitionManager.ownedPartitions()).thenReturn(List.of(7));
        when(queue.popDueRetryItems(eq(7), any(), eq(20)))
                .thenReturn(List.of(new RetryDueItem(9L, 1001L, 1, 11L)));
        when(queue.requeueRetryUnit(1001L, 1, 11L, 0D)).thenReturn(true);

        RetryQueueScheduler scheduler = new RetryQueueScheduler(
                partitionManager,
                queue,
                properties,
                metrics,
                activationService
        );
        scheduler.requeueDueRetries();

        verify(queue).popDueRetryItems(eq(7), any(), eq(20));
        verify(queue).requeueRetryUnit(1001L, 1, 11L, 0D);
        verify(activationService).activate(9L, 1001L);
    }
}
