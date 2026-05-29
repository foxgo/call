package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingDialUnitActivationJobTest {

    @Test
    void shouldActivateOnlyDuePendingTasksOwnedByCurrentPartitions() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setPartitionCount(8);
        properties.setPreloadBatchSize(20);

        when(partitionManager.ownedPartitions()).thenReturn(List.of(1));
        when(repository.findDuePendingTasks(any(), eq(20))).thenReturn(List.of(
                new CallDialUnitRepository.DuePendingTask(9L, 1001L),
                new CallDialUnitRepository.DuePendingTask(9L, 1002L)
        ));

        PendingDialUnitActivationJob job = new PendingDialUnitActivationJob(
                partitionManager,
                repository,
                activationService,
                properties
        );

        job.activateDuePendingTasks();

        verify(activationService).activate(9L, 1001L);
        verify(activationService, never()).activate(9L, 1002L);
    }
}
