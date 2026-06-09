package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTaskDispatcherTest {

    @Test
    void shouldSubmitOwnedPartitionsToExecutorAndDrainEachPartitionUntilPerTickLimit() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        PartitionSchedulerWorker worker = mock(PartitionSchedulerWorker.class);
        when(partitionManager.ownedPartitions()).thenReturn(List.of(7, 8));
        when(worker.runPartition(7)).thenReturn(true, true, false);
        when(worker.runPartition(8)).thenReturn(true, true, true);

        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setMaxTasksPerPartitionTick(3);
        RecordingExecutor executor = new RecordingExecutor();
        CallTaskDispatcher dispatcher = new CallTaskDispatcher(partitionManager, worker, properties, executor);

        dispatcher.dispatchOwnedPartitions();

        assertEquals(2, executor.size());
        verify(worker, never()).runPartition(7);
        verify(worker, never()).runPartition(8);

        executor.runAll();

        verify(worker, times(3)).runPartition(7);
        verify(worker, times(3)).runPartition(8);
    }

    @Test
    void shouldNotResubmitPartitionWhilePreviousDrainIsStillRunning() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        PartitionSchedulerWorker worker = mock(PartitionSchedulerWorker.class);
        when(partitionManager.ownedPartitions()).thenReturn(List.of(7));
        when(worker.runPartition(7)).thenReturn(false);

        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        RecordingExecutor executor = new RecordingExecutor();
        CallTaskDispatcher dispatcher = new CallTaskDispatcher(partitionManager, worker, properties, executor);

        dispatcher.dispatchOwnedPartitions();
        dispatcher.dispatchOwnedPartitions();

        assertEquals(1, executor.size());
        verify(worker, never()).runPartition(7);
    }

    @Test
    void shouldAllowPartitionToBeSubmittedAgainAfterDrainCompletes() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        PartitionSchedulerWorker worker = mock(PartitionSchedulerWorker.class);
        when(partitionManager.ownedPartitions()).thenReturn(List.of(7));
        when(worker.runPartition(7)).thenReturn(false);

        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        RecordingExecutor executor = new RecordingExecutor();
        CallTaskDispatcher dispatcher = new CallTaskDispatcher(partitionManager, worker, properties, executor);

        dispatcher.dispatchOwnedPartitions();
        executor.runAll();
        dispatcher.dispatchOwnedPartitions();

        assertEquals(1, executor.size());
        verify(worker, times(1)).runPartition(7);
    }

    @Test
    void shouldStopSubmittingAfterExecutorRejectionAndResumeFromRejectedPartitionNextTick() {
        TaskPartitionManager partitionManager = mock(TaskPartitionManager.class);
        PartitionSchedulerWorker worker = mock(PartitionSchedulerWorker.class);
        when(partitionManager.ownedPartitions()).thenReturn(List.of(7, 8, 9));
        when(worker.runPartition(7)).thenReturn(false);
        when(worker.runPartition(8)).thenReturn(false);
        when(worker.runPartition(9)).thenReturn(false);

        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        LimitedPerTickExecutor executor = new LimitedPerTickExecutor(1);
        CallTaskDispatcher dispatcher = new CallTaskDispatcher(partitionManager, worker, properties, executor);

        dispatcher.dispatchOwnedPartitions();

        assertEquals(2, executor.attempts());
        executor.runAll();
        verify(worker, times(1)).runPartition(7);
        verify(worker, never()).runPartition(8);
        verify(worker, never()).runPartition(9);

        executor.resetCapacity(1);
        dispatcher.dispatchOwnedPartitions();

        assertEquals(4, executor.attempts());
        executor.runAll();
        verify(worker, times(1)).runPartition(7);
        verify(worker, times(1)).runPartition(8);
        verify(worker, never()).runPartition(9);
    }

    private static final class RecordingExecutor implements java.util.concurrent.Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private int size() {
            return tasks.size();
        }

        private void runAll() {
            List<Runnable> submitted = List.copyOf(tasks);
            tasks.clear();
            submitted.forEach(Runnable::run);
        }
    }

    private static final class LimitedPerTickExecutor implements java.util.concurrent.Executor {
        private final List<Runnable> tasks = new ArrayList<>();
        private int remainingCapacity;
        private int attempts;

        private LimitedPerTickExecutor(int remainingCapacity) {
            this.remainingCapacity = remainingCapacity;
        }

        @Override
        public void execute(Runnable command) {
            attempts++;
            if (remainingCapacity <= 0) {
                throw new RejectedExecutionException("executor full");
            }
            remainingCapacity--;
            tasks.add(command);
        }

        private int attempts() {
            return attempts;
        }

        private void resetCapacity(int remainingCapacity) {
            this.remainingCapacity = remainingCapacity;
        }

        private void runAll() {
            List<Runnable> submitted = List.copyOf(tasks);
            tasks.clear();
            submitted.forEach(Runnable::run);
        }
    }
}
