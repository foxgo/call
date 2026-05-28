package com.callcenter.task.dispatch;

import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessingTimeoutRecoveryJob {

    private final TaskPartitionManager taskPartitionManager;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final CallDialUnitRepository callDialUnitRepository;
    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final ShardingRouter shardingRouter;
    private final CallTaskDispatchProperties properties;
    private final CallTaskMetrics metrics;
    private final TaskActivationService taskActivationService;

    public ProcessingTimeoutRecoveryJob(
            TaskPartitionManager taskPartitionManager,
            RedisDialUnitQueue redisDialUnitQueue,
            CallDialUnitRepository callDialUnitRepository,
            DispatchConcurrencyLimiter concurrencyLimiter,
            ShardingRouter shardingRouter,
            CallTaskDispatchProperties properties,
            CallTaskMetrics metrics,
            TaskActivationService taskActivationService
    ) {
        this.taskPartitionManager = taskPartitionManager;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.callDialUnitRepository = callDialUnitRepository;
        this.concurrencyLimiter = concurrencyLimiter;
        this.shardingRouter = shardingRouter;
        this.properties = properties;
        this.metrics = metrics;
        this.taskActivationService = taskActivationService;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.processing-recovery-interval:PT5S}")
    public void recoverExpiredProcessing() {
        Instant now = Instant.now();
        for (int partition : taskPartitionManager.ownedPartitions()) {
            for (ProcessingTimeoutItem item : redisDialUnitQueue.popExpiredProcessingItems(partition, now, properties.getDispatchBatchSize())) {
                if (!redisDialUnitQueue.recoverExpiredProcessingUnit(item.taskId(), item.shard(), item.dialUnitId(), 0D)) {
                    continue;
                }
                ShardKey shardKey = shardingRouter.routeDialUnit(item.tenantId(), item.taskId());
                int recovered = callDialUnitRepository.markRecoveredForRetry(
                        shardKey,
                        item.taskId(),
                        List.of(item.dialUnitId()),
                        now.plus(properties.getRetryBackoff())
                );
                metrics.incrementProcessingRecovered(recovered);
                for (int i = 0; i < recovered; i++) {
                    concurrencyLimiter.release(item.tenantId(), item.taskId());
                }
                if (recovered > 0) {
                    taskActivationService.activate(item.tenantId(), item.taskId());
                }
            }
        }
    }
}
