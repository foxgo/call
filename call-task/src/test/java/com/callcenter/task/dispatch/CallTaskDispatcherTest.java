package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTaskDispatcherTest {

    @Test
    void shouldDispatchOwnedPartitionsUntilPerTickLimit() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        PartitionSchedulerWorker worker = mock(PartitionSchedulerWorker.class);
        when(partitionManager.ownedPartitions()).thenReturn(List.of(7, 8));
        when(worker.runPartition(7)).thenReturn(true, true, false);
        when(worker.runPartition(8)).thenReturn(true, true, true);

        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setMaxTasksPerPartitionTick(3);
        CallTaskDispatcher dispatcher = new CallTaskDispatcher(partitionManager, worker, properties);

        dispatcher.dispatchOwnedPartitions();

        verify(worker, times(3)).runPartition(7);
        verify(worker, times(3)).runPartition(8);
    }
}
