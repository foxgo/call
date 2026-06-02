package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.mq.DialDispatchPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class AsyncDialDispatchServiceTest {

    @Test
    void shouldSubmitPublishWorkToExecutor() {
        RecordingExecutor executor = new RecordingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        AsyncDialDispatchService service = new AsyncDialDispatchService(executor, publisher, compensationService, metrics);
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();

        service.submit(shardKey, unit);

        assertEquals(1, executor.size());
        verify(publisher, never()).publish(unit);

        executor.runAll();

        verify(publisher).publish(unit);
        verify(metrics).incrementDispatchPublished();
        verify(compensationService, never()).compensateFailedDispatch(shardKey, unit);
    }

    @Test
    void shouldCompensateFailedPublish() {
        RecordingExecutor executor = new RecordingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        AsyncDialDispatchService service = new AsyncDialDispatchService(executor, publisher, compensationService, metrics);
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();
        doThrow(new RuntimeException("send failed")).when(publisher).publish(unit);

        service.submit(shardKey, unit);
        executor.runAll();

        verify(metrics).incrementDispatchSendFailed();
        verify(compensationService).compensateFailedDispatch(shardKey, unit);
        verify(metrics, never()).incrementDispatchPublished();
    }

    @Test
    void shouldCompensateImmediatelyWhenExecutorRejectsSubmission() {
        RejectingExecutor executor = new RejectingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        AsyncDialDispatchService service = new AsyncDialDispatchService(executor, publisher, compensationService, metrics);
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();

        service.submit(shardKey, unit);

        verify(metrics).incrementDispatchSendRejected();
        verify(compensationService).compensateFailedDispatch(shardKey, unit);
        verify(metrics, never()).incrementDispatchPublished();
        verify(metrics, never()).incrementDispatchSendFailed();
        verify(publisher, never()).publish(unit);
    }

    private static CallDialUnitEntity unit() {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(11L);
        unit.setTenantId(9L);
        unit.setTaskId(1001L);
        unit.setDispatchToken("token-1");
        unit.setPhone("138001380011");
        return unit;
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

    private static final class RejectingExecutor implements java.util.concurrent.Executor {
        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("rejected");
        }
    }
}
