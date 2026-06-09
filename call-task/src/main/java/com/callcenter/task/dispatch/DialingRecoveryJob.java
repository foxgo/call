package com.callcenter.task.dispatch;

import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DialingRecoveryJob {

    private final TaskPartitionManager taskPartitionManager;
    private final CallDialUnitRepository callDialUnitRepository;
    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final ShardingRouter shardingRouter;
    private final CallTaskDispatchProperties properties;
    private final CallTaskMetrics metrics;
    private final TaskActivationService taskActivationService;
    private final TaskPartitioner taskPartitioner;

    public DialingRecoveryJob(
            TaskPartitionManager taskPartitionManager,
            CallDialUnitRepository callDialUnitRepository,
            DispatchConcurrencyLimiter concurrencyLimiter,
            ShardingRouter shardingRouter,
            CallTaskDispatchProperties properties,
            CallTaskMetrics metrics,
            TaskActivationService taskActivationService
    ) {
        this.taskPartitionManager = taskPartitionManager;
        this.callDialUnitRepository = callDialUnitRepository;
        this.concurrencyLimiter = concurrencyLimiter;
        this.shardingRouter = shardingRouter;
        this.properties = properties;
        this.metrics = metrics;
        this.taskActivationService = taskActivationService;
        this.taskPartitioner = new TaskPartitioner(properties.getPartitionCount());
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.processing-recovery-interval:PT5S}")
    public void recoverExpiredDialing() {
        Set<Integer> ownedPartitions = taskPartitionManager.ownedPartitions().stream().collect(Collectors.toSet());
        if (ownedPartitions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Instant retryAt = now.toInstant(ZoneOffset.UTC).plus(properties.getRetryBackoff());
        for (CallDialUnitRepository.ExpiredDialingBatch batch : callDialUnitRepository.findExpiredDialingBatches(
                now,
                properties.getDispatchBatchSize()
        )) {
            if (!ownedPartitions.contains(taskPartitioner.partitionOf(batch.taskId()))) {
                continue;
            }
            ShardKey shardKey = shardingRouter.routeDialUnit(batch.tenantId(), batch.taskId());
            int recovered = callDialUnitRepository.markRecoveredForRetry(
                    shardKey,
                    batch.taskId(),
                    batch.dialUnitIds(),
                    retryAt
            );
            metrics.incrementProcessingRecovered(recovered);
            if (recovered > 0) {
                concurrencyLimiter.releaseBatch(batch.tenantId(), batch.taskId(), recovered);
                taskActivationService.activate(batch.tenantId(), batch.taskId());
            }
        }
    }
}
