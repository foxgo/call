package com.callcenter.task.dispatch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.callcenter.task.entity.CallDialUnitEntity;
import com.callcenter.persistence.route.ShardKey;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.mq.DialDispatchPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class AsyncDialDispatchServiceTest {

    @Test
    void shouldSubmitPublishWorkToExecutor() {
        RecordingExecutor executor = new RecordingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        DispatchUnitValidator validator = mock(DispatchUnitValidator.class);
        DispatchGateService gateService = mock(DispatchGateService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        AsyncDialDispatchService service = new AsyncDialDispatchService(
                executor,
                publisher,
                compensationService,
                validator,
                gateService,
                metrics
        );
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();
        when(gateService.evaluate(shardKey, unit)).thenReturn(DispatchGateDecision.allow());

        service.submit(shardKey, unit);

        assertEquals(1, executor.size());
        verify(publisher, never()).publish(unit);

        executor.runAll();

        verify(validator).validate(unit);
        verify(gateService).evaluate(shardKey, unit);
        verify(publisher).publish(unit);
        verify(metrics).incrementDispatchPublished();
        verify(compensationService, never()).compensateFailedDispatch(shardKey, unit);
    }

    @Test
    void shouldCompensateFailedPublish() {
        RecordingExecutor executor = new RecordingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        DispatchUnitValidator validator = mock(DispatchUnitValidator.class);
        DispatchGateService gateService = mock(DispatchGateService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        AsyncDialDispatchService service = new AsyncDialDispatchService(
                executor,
                publisher,
                compensationService,
                validator,
                gateService,
                metrics
        );
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();
        when(gateService.evaluate(shardKey, unit)).thenReturn(DispatchGateDecision.allow());
        doThrow(new RuntimeException("send failed")).when(publisher).publish(unit);

        service.submit(shardKey, unit);
        executor.runAll();

        verify(metrics).incrementDispatchSendFailed();
        verify(compensationService).compensateFailedDispatch(shardKey, unit);
        verify(metrics, never()).incrementDispatchPublished();
    }

    @Test
    void shouldCompensateWhenValidationFails() {
        RecordingExecutor executor = new RecordingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        DispatchUnitValidator validator = mock(DispatchUnitValidator.class);
        DispatchGateService gateService = mock(DispatchGateService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        AsyncDialDispatchService service = new AsyncDialDispatchService(
                executor,
                publisher,
                compensationService,
                validator,
                gateService,
                metrics
        );
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();
        doThrow(new DispatchPreparationException("selectedCallerNumber is blank")).when(validator).validate(unit);

        service.submit(shardKey, unit);
        executor.runAll();

        verify(metrics).incrementDispatchValidationFailed();
        verify(compensationService).compensateFailedDispatch(shardKey, unit);
        verify(gateService, never()).evaluate(any(), any());
        verify(publisher, never()).publish(unit);
    }

    @Test
    void shouldCompensateWhenGateRejectsDispatch() {
        RecordingExecutor executor = new RecordingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        DispatchUnitValidator validator = mock(DispatchUnitValidator.class);
        DispatchGateService gateService = mock(DispatchGateService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        AsyncDialDispatchService service = new AsyncDialDispatchService(
                executor,
                publisher,
                compensationService,
                validator,
                gateService,
                metrics
        );
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();
        when(gateService.evaluate(shardKey, unit)).thenReturn(DispatchGateDecision.reject("TASK_NOT_RUNNING"));

        service.submit(shardKey, unit);
        executor.runAll();

        verify(metrics).incrementDispatchGateRejected();
        verify(compensationService).compensateFailedDispatch(shardKey, unit);
        verify(publisher, never()).publish(unit);
        verify(metrics, never()).incrementDispatchPublished();
        verify(metrics, never()).incrementDispatchSendFailed();
    }

    @Test
    void shouldCompensateImmediatelyWhenExecutorRejectsSubmission() {
        RejectingExecutor executor = new RejectingExecutor();
        DialDispatchPublisher publisher = mock(DialDispatchPublisher.class);
        DialDispatchCompensationService compensationService = mock(DialDispatchCompensationService.class);
        DispatchUnitValidator validator = mock(DispatchUnitValidator.class);
        DispatchGateService gateService = mock(DispatchGateService.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        Logger logger = (Logger) LoggerFactory.getLogger(AsyncDialDispatchService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        AsyncDialDispatchService service = new AsyncDialDispatchService(
                executor,
                publisher,
                compensationService,
                validator,
                gateService,
                metrics
        );
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = unit();

        service.submit(shardKey, unit);

        verify(metrics).incrementDispatchSendRejected();
        verify(compensationService).compensateFailedDispatch(shardKey, unit);
        verify(metrics, never()).incrementDispatchPublished();
        verify(metrics, never()).incrementDispatchSendFailed();
        verify(publisher, never()).publish(unit);
        assertEquals(1, appender.list.size());
        assertEquals(Level.WARN, appender.list.getFirst().getLevel());
        assertEquals(
                "event=dispatch_submission_rejected taskId=1001 dialUnitId=11 token=******",
                appender.list.getFirst().getFormattedMessage()
        );
        logger.detachAppender(appender);
    }

    private static CallDialUnitEntity unit() {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(11L);
        unit.setTenantId(9L);
        unit.setTaskId(1001L);
        unit.setDispatchToken("token-1");
        unit.setPhone("138001380011");
        unit.setSelectedCallerNumber("02166668888");
        unit.setAttemptStage("FIRST_ATTEMPT");
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
