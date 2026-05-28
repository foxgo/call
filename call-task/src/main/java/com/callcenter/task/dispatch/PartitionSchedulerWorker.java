package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.mq.DialDispatchPublisher;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PartitionSchedulerWorker {

    private final ActiveTaskQueue activeTaskQueue;
    private final CallTaskRepository callTaskRepository;
    private final DialUnitPreloadService dialUnitPreloadService;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final CallDialUnitRepository callDialUnitRepository;
    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final DialDispatchPublisher dialDispatchPublisher;
    private final CallTaskDispatchProperties properties;
    private final ShardingRouter shardingRouter;
    private final CallTaskMetrics metrics;

    public PartitionSchedulerWorker(
            ActiveTaskQueue activeTaskQueue,
            CallTaskRepository callTaskRepository,
            DialUnitPreloadService dialUnitPreloadService,
            RedisDialUnitQueue redisDialUnitQueue,
            CallDialUnitRepository callDialUnitRepository,
            DispatchConcurrencyLimiter concurrencyLimiter,
            DialDispatchPublisher dialDispatchPublisher,
            CallTaskDispatchProperties properties,
            ShardingRouter shardingRouter,
            CallTaskMetrics metrics
    ) {
        this.activeTaskQueue = activeTaskQueue;
        this.callTaskRepository = callTaskRepository;
        this.dialUnitPreloadService = dialUnitPreloadService;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.callDialUnitRepository = callDialUnitRepository;
        this.concurrencyLimiter = concurrencyLimiter;
        this.dialDispatchPublisher = dialDispatchPublisher;
        this.properties = properties;
        this.shardingRouter = shardingRouter;
        this.metrics = metrics;
    }

    public void runPartition(int partition) {
        Optional<Long> taskId = activeTaskQueue.pollNextTask(partition);
        if (taskId.isEmpty()) {
            return;
        }

        Optional<TaskSchedulingMeta> meta = activeTaskQueue.loadMeta(taskId.get());
        if (meta.isEmpty()) {
            return;
        }

        CallTaskEntity task = callTaskRepository.findRequired(meta.get().tenantId(), taskId.get());
        dialUnitPreloadService.preloadRunningTask(task);

        int available = concurrencyLimiter.available(task.getTenantId(), task.getId(), task.getMaxConcurrency());
        int budget = Math.min(available, properties.getDispatchBatchSize());
        if (budget <= 0) {
            return;
        }

        ShardKey shardKey = shardingRouter.routeDialUnit(task.getTenantId(), task.getId());
        List<Long> ids = redisDialUnitQueue.claimReady(
                task.getTenantId(),
                task.getId(),
                shardKey.tableIndex(),
                budget,
                Instant.now().plus(properties.getProcessingTimeout())
        );
        if (ids.isEmpty()) {
            return;
        }

        String dispatchToken = UUID.randomUUID().toString();
        List<CallDialUnitEntity> units = callDialUnitRepository.markDialing(
                shardKey,
                task.getId(),
                ids,
                dispatchToken,
                LocalDateTime.now(),
                LocalDateTime.now().plus(properties.getProcessingTimeout())
        );

        for (CallDialUnitEntity unit : units) {
            dialDispatchPublisher.publish(unit);
            metrics.incrementDispatchPublished();
        }
    }
}
