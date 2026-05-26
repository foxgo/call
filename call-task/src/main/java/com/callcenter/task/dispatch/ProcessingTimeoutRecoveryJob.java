package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessingTimeoutRecoveryJob {

    private final CallTaskRepository callTaskRepository;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final CallDialUnitRepository callDialUnitRepository;
    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final ShardingRouter shardingRouter;
    private final CallTaskDispatchProperties properties;
    private final CallTaskMetrics metrics;

    public ProcessingTimeoutRecoveryJob(
            CallTaskRepository callTaskRepository,
            RedisDialUnitQueue redisDialUnitQueue,
            CallDialUnitRepository callDialUnitRepository,
            DispatchConcurrencyLimiter concurrencyLimiter,
            ShardingRouter shardingRouter,
            CallTaskDispatchProperties properties,
            CallTaskMetrics metrics
    ) {
        this.callTaskRepository = callTaskRepository;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.callDialUnitRepository = callDialUnitRepository;
        this.concurrencyLimiter = concurrencyLimiter;
        this.shardingRouter = shardingRouter;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.processing-recovery-interval:PT5S}")
    public void recoverExpiredProcessing() {
        Instant now = Instant.now();
        for (CallTaskEntity task : callTaskRepository.loadRunningTasks()) {
            ShardKey shardKey = shardingRouter.routeDialUnit(task.getTenantId(), task.getId());
            List<Long> ids = redisDialUnitQueue.recoverExpiredProcessing(
                    task.getId(),
                    shardKey.tableIndex(),
                    now,
                    properties.getDispatchBatchSize(),
                    now.toEpochMilli()
            );
            if (ids.isEmpty()) {
                continue;
            }
            callDialUnitRepository.markRecoveredForRetry(
                    shardKey,
                    task.getId(),
                    ids,
                    now.plus(properties.getRetryBackoff())
            );
            metrics.incrementProcessingRecovered(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                concurrencyLimiter.release(task.getTenantId(), task.getId());
            }
        }
    }
}
