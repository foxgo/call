package com.callcenter.task.service;

import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
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

    public DialResultWritebackService(
            CallDialUnitRepository callDialUnitRepository,
            RedisDialUnitQueue redisDialUnitQueue,
            DispatchConcurrencyLimiter concurrencyLimiter,
            CallTaskDispatchProperties properties,
            ShardingRouter shardingRouter,
            CallTaskMetrics metrics,
            TaskActivationService taskActivationService
    ) {
        this.callDialUnitRepository = callDialUnitRepository;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.concurrencyLimiter = concurrencyLimiter;
        this.properties = properties;
        this.shardingRouter = shardingRouter;
        this.metrics = metrics;
        this.taskActivationService = taskActivationService;
    }

    @Transactional
    public void handleCallback(Long tenantId, DialResultCallbackRequest request) {
        ShardKey shardKey = shardingRouter.routeDialUnit(tenantId, request.getTaskId());
        if (request.isSuccess()) {
            boolean updated = callDialUnitRepository.markSuccess(
                    shardKey,
                    request.getTaskId(),
                    request.getDialUnitId(),
                    request.getDispatchToken()
            );
            if (updated) {
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
                Instant.now().plus(properties.getRetryBackoff())
        );
        if (!decision.processed()) {
            return;
        }
        concurrencyLimiter.release(tenantId, request.getTaskId());
        metrics.incrementWritebackFailure(request.getTaskId());
        taskActivationService.activate(tenantId, request.getTaskId());
    }
}
