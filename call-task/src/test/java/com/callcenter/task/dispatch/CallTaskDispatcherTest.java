package com.callcenter.task.dispatch;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTaskDispatcherTest {

    @Test
    void shouldDispatchOwnedPartitionsWithoutScanningRunningTasks() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        PartitionSchedulerWorker worker = mock(PartitionSchedulerWorker.class);
        when(partitionManager.ownedPartitions()).thenReturn(List.of(7, 8));

        CallTaskDispatcher dispatcher = new CallTaskDispatcher(partitionManager, worker);

        dispatcher.dispatchOwnedPartitions();

        verify(worker).runPartition(7);
        verify(worker).runPartition(8);
    }
}
