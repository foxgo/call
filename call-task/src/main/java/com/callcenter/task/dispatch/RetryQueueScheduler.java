package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryQueueScheduler {

    private final TaskPartitionManager taskPartitionManager;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final CallTaskDispatchProperties properties;
    private final CallTaskMetrics metrics;
    private final TaskActivationService taskActivationService;

    public RetryQueueScheduler(
            TaskPartitionManager taskPartitionManager,
            RedisDialUnitQueue redisDialUnitQueue,
            CallTaskDispatchProperties properties,
            CallTaskMetrics metrics,
            TaskActivationService taskActivationService
    ) {
        this.taskPartitionManager = taskPartitionManager;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.properties = properties;
        this.metrics = metrics;
        this.taskActivationService = taskActivationService;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.retry-scan-interval:PT5S}")
    public void requeueDueRetries() {
        Instant now = Instant.now();
        for (int partition : taskPartitionManager.ownedPartitions()) {
            for (RetryDueItem item : redisDialUnitQueue.popDueRetryItems(partition, now, properties.getDispatchBatchSize())) {
                if (redisDialUnitQueue.requeueRetryUnit(item.taskId(), item.shard(), item.dialUnitId(), 0D)) {
                    metrics.incrementRetryRequeued(1);
                    taskActivationService.activate(item.tenantId(), item.taskId());
                }
            }
        }
    }
}
