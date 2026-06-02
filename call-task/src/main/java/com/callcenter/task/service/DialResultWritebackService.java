package com.callcenter.task.service;

import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.caller.AttemptStage;
import com.callcenter.task.caller.CallerIdHealthEvent;
import com.callcenter.task.caller.CallerIdHealthService;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.dispatch.DispatchConcurrencyLimiter;
import com.callcenter.task.dispatch.RedisDialUnitQueue;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.model.DialResultCallbackRequest;
import com.callcenter.task.model.RetryDecision;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DialResultWritebackService {

    private final CallDialUnitRepository callDialUnitRepository;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final CallTaskDispatchProperties properties;
    private final ShardingRouter shardingRouter;
    private final CallTaskMetrics metrics;
    private final TaskActivationService taskActivationService;
    private final CallerIdHealthService callerIdHealthService;

    public DialResultWritebackService(
            CallDialUnitRepository callDialUnitRepository,
            RedisDialUnitQueue redisDialUnitQueue,
            DispatchConcurrencyLimiter concurrencyLimiter,
            CallTaskDispatchProperties properties,
            ShardingRouter shardingRouter,
            CallTaskMetrics metrics,
            TaskActivationService taskActivationService,
            CallerIdHealthService callerIdHealthService
    ) {
        this.callDialUnitRepository = callDialUnitRepository;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.concurrencyLimiter = concurrencyLimiter;
        this.properties = properties;
        this.shardingRouter = shardingRouter;
        this.metrics = metrics;
        this.taskActivationService = taskActivationService;
        this.callerIdHealthService = callerIdHealthService;
    }

    @Transactional
    public void handleCallback(Long tenantId, DialResultCallbackRequest request) {
        ShardKey shardKey = shardingRouter.routeDialUnit(tenantId, request.getTaskId());
        var dialingUnit = callDialUnitRepository.findDialingByDispatchToken(
                shardKey,
                request.getTaskId(),
                request.getDialUnitId(),
                request.getDispatchToken()
        );
        if (dialingUnit == null) {
            return;
        }
        if (request.isSuccess()) {
            boolean updated = callDialUnitRepository.markSuccess(
                    shardKey,
                    request.getTaskId(),
                    request.getDialUnitId(),
                    request.getDispatchToken(),
                    request.getRingDurationSeconds(),
                    request.getTalkDurationSeconds(),
                    request.getHangupCode()
            );
            if (updated) {
                recordHealthEvent(tenantId, dialingUnit, request);
                concurrencyLimiter.release(tenantId, request.getTaskId());
                metrics.incrementWritebackSuccess(request.getTaskId());
                taskActivationService.activate(tenantId, request.getTaskId());
            }
            return;
        }

        RetryDecision decision = callDialUnitRepository.markFailedForRetry(
                shardKey,
                request.getTaskId(),
                request.getDialUnitId(),
                request.getDispatchToken(),
                request.getFailureCode(),
                request.getFailureReason(),
                Instant.now().plus(properties.getRetryBackoff()),
                request.getRingDurationSeconds(),
                request.getTalkDurationSeconds(),
                request.getHangupCode()
        );
        if (!decision.processed()) {
            return;
        }
        recordHealthEvent(tenantId, dialingUnit, request);
        concurrencyLimiter.release(tenantId, request.getTaskId());
        metrics.incrementWritebackFailure(request.getTaskId());
        taskActivationService.activate(tenantId, request.getTaskId());
    }

    private void recordHealthEvent(Long tenantId, com.callcenter.common.entity.CallDialUnitEntity dialingUnit, DialResultCallbackRequest request) {
        if (dialingUnit.getSelectedCallerId() == null) {
            return;
        }
        AttemptStage attemptStage = dialingUnit.getAttemptStage() == null
                ? AttemptStage.fromRetryCount(dialingUnit.getRetryCount())
                : AttemptStage.valueOf(dialingUnit.getAttemptStage());
        callerIdHealthService.recordFeedback(new CallerIdHealthEvent(
                tenantId,
                dialingUnit.getSelectedCallerId(),
                attemptStage,
                request.isSuccess(),
                request.getRingDurationSeconds(),
                request.getTalkDurationSeconds(),
                request.getFailureCode()
        ));
    }
}
