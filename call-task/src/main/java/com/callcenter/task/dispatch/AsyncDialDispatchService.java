package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.mq.DialDispatchPublisher;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AsyncDialDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AsyncDialDispatchService.class);

    private final Executor dispatchSendExecutor;
    private final DialDispatchPublisher dialDispatchPublisher;
    private final DialDispatchCompensationService dialDispatchCompensationService;
    private final DispatchUnitValidator dispatchUnitValidator;
    private final DispatchGateService dispatchGateService;
    private final CallTaskMetrics metrics;

    public AsyncDialDispatchService(
            @Qualifier("callTaskDispatchSendExecutor") Executor dispatchSendExecutor,
            DialDispatchPublisher dialDispatchPublisher,
            DialDispatchCompensationService dialDispatchCompensationService,
            DispatchUnitValidator dispatchUnitValidator,
            DispatchGateService dispatchGateService,
            CallTaskMetrics metrics
    ) {
        this.dispatchSendExecutor = dispatchSendExecutor;
        this.dialDispatchPublisher = dialDispatchPublisher;
        this.dialDispatchCompensationService = dialDispatchCompensationService;
        this.dispatchUnitValidator = dispatchUnitValidator;
        this.dispatchGateService = dispatchGateService;
        this.metrics = metrics;
    }

    public void submit(ShardKey shardKey, CallDialUnitEntity unit) {
        try {
            dispatchSendExecutor.execute(() -> doSubmit(shardKey, unit));
        } catch (RejectedExecutionException ex) {
            log.warn(
                    "Async dial dispatch submission rejected, taskId={}, dialUnitId={}, token={}",
                    unit == null ? null : unit.getTaskId(),
                    unit == null ? null : unit.getId(),
                    unit == null ? null : unit.getDispatchToken(),
                    ex
            );
            metrics.incrementDispatchSendRejected();
            compensateQuietly(shardKey, unit);
        }
    }

    private void doSubmit(ShardKey shardKey, CallDialUnitEntity unit) {
        try {
            dispatchUnitValidator.validate(unit);
            DispatchGateDecision decision = dispatchGateService.evaluate(shardKey, unit);
            if (!decision.allowed()) {
                log.warn(
                        "Dispatch gate rejected, taskId={}, dialUnitId={}, token={}, reason={}",
                        unit.getTaskId(),
                        unit.getId(),
                        unit.getDispatchToken(),
                        decision.reason()
                );
                metrics.incrementDispatchGateRejected();
                dialDispatchCompensationService.compensateFailedDispatch(shardKey, unit);
                return;
            }
            dialDispatchPublisher.publish(unit);
            metrics.incrementDispatchPublished();
        } catch (DispatchPreparationException ex) {
            log.warn(
                    "Dispatch validation failed, taskId={}, dialUnitId={}, token={}, reason={}",
                    unit == null ? null : unit.getTaskId(),
                    unit == null ? null : unit.getId(),
                    unit == null ? null : unit.getDispatchToken(),
                    ex.getMessage()
            );
            metrics.incrementDispatchValidationFailed();
            compensateQuietly(shardKey, unit);
        } catch (Exception ex) {
            log.warn(
                    "Async dial dispatch publish failed, taskId={}, dialUnitId={}, token={}",
                    unit == null ? null : unit.getTaskId(),
                    unit == null ? null : unit.getId(),
                    unit == null ? null : unit.getDispatchToken(),
                    ex
            );
            metrics.incrementDispatchSendFailed();
            compensateQuietly(shardKey, unit);
        }
    }

    private void compensateQuietly(ShardKey shardKey, CallDialUnitEntity unit) {
        if (shardKey == null || unit == null) {
            return;
        }
        dialDispatchCompensationService.compensateFailedDispatch(shardKey, unit);
    }
}
