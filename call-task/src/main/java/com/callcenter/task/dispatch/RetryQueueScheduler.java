package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryQueueScheduler {

    private final CallTaskRepository callTaskRepository;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final ShardingRouter shardingRouter;
    private final CallTaskDispatchProperties properties;
    private final CallTaskMetrics metrics;

    public RetryQueueScheduler(
            CallTaskRepository callTaskRepository,
            RedisDialUnitQueue redisDialUnitQueue,
            ShardingRouter shardingRouter,
            CallTaskDispatchProperties properties,
            CallTaskMetrics metrics
    ) {
        this.callTaskRepository = callTaskRepository;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.shardingRouter = shardingRouter;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.retry-scan-interval:PT5S}")
    public void requeueDueRetries() {
        Instant now = Instant.now();
        for (CallTaskEntity task : callTaskRepository.loadRunningTasks()) {
            ShardKey shardKey = shardingRouter.routeDialUnit(task.getTenantId(), task.getId());
            List<Long> ids = redisDialUnitQueue.requeueDueRetry(
                    task.getId(),
                    shardKey.tableIndex(),
                    now,
                    properties.getDispatchBatchSize(),
                    now.toEpochMilli()
            );
            metrics.incrementRetryRequeued(ids.size());
        }
    }
}
