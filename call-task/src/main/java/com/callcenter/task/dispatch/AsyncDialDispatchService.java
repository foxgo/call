package com.callcenter.task.dispatch;

import com.callcenter.task.entity.CallDialUnitEntity;
import com.callcenter.persistence.route.ShardKey;
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
                    "event=dispatch_submission_rejected taskId={} dialUnitId={} token={}",
                    unit == null ? null : unit.getTaskId(),
                    unit == null ? null : unit.getId(),
                    maskDispatchToken(unit),
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
                        "event=dispatch_gate_rejected taskId={} dialUnitId={} token={} reason={}",
                        unit.getTaskId(),
                        unit.getId(),
                        maskDispatchToken(unit),
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
                    "event=dispatch_validation_failed taskId={} dialUnitId={} token={} reason={}",
                    unit == null ? null : unit.getTaskId(),
                    unit == null ? null : unit.getId(),
                    maskDispatchToken(unit),
                    ex.getMessage()
            );
            metrics.incrementDispatchValidationFailed();
            compensateQuietly(shardKey, unit);
        } catch (Exception ex) {
            log.warn(
                    "event=dispatch_publish_failed taskId={} dialUnitId={} token={}",
                    unit == null ? null : unit.getTaskId(),
                    unit == null ? null : unit.getId(),
                    maskDispatchToken(unit),
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

    private String maskDispatchToken(CallDialUnitEntity unit) {
        return unit == null || unit.getDispatchToken() == null ? null : "******";
    }
}
