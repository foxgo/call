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
    private final CallTaskMetrics metrics;

    public AsyncDialDispatchService(
            @Qualifier("callTaskDispatchSendExecutor") Executor dispatchSendExecutor,
            DialDispatchPublisher dialDispatchPublisher,
            DialDispatchCompensationService dialDispatchCompensationService,
            CallTaskMetrics metrics
    ) {
        this.dispatchSendExecutor = dispatchSendExecutor;
        this.dialDispatchPublisher = dialDispatchPublisher;
        this.dialDispatchCompensationService = dialDispatchCompensationService;
        this.metrics = metrics;
    }

    public void submit(ShardKey shardKey, CallDialUnitEntity unit) {
        try {
            dispatchSendExecutor.execute(() -> {
                try {
                    dialDispatchPublisher.publish(unit);
                    metrics.incrementDispatchPublished();
                } catch (Exception ex) {
                    log.warn(
                            "Async dial dispatch publish failed, taskId={}, dialUnitId={}, token={}",
                            unit.getTaskId(),
                            unit.getId(),
                            unit.getDispatchToken(),
                            ex
                    );
                    metrics.incrementDispatchSendFailed();
                    dialDispatchCompensationService.compensateFailedDispatch(shardKey, unit);
                }
            });
        } catch (RejectedExecutionException ex) {
            log.warn(
                    "Async dial dispatch submission rejected, taskId={}, dialUnitId={}, token={}",
                    unit.getTaskId(),
                    unit.getId(),
                    unit.getDispatchToken(),
                    ex
            );
            metrics.incrementDispatchSendRejected();
            dialDispatchCompensationService.compensateFailedDispatch(shardKey, unit);
        }
    }
}
