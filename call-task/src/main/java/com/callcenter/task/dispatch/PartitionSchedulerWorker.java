package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.enums.CallTaskStatus;
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
        if (!CallTaskStatus.RUNNING.name().equals(task.getStatus())) {
            activeTaskQueue.block(task.getId(), TaskBlockReason.PAUSED);
            return;
        }
        dialUnitPreloadService.preloadRunningTask(task);

        int requested = Math.min(properties.getDispatchBatchSize(), task.getMaxConcurrency());
        int granted = concurrencyLimiter.tryAcquireBatch(task.getTenantId(), task.getId(), task.getMaxConcurrency(), requested);
        if (granted <= 0) {
            activeTaskQueue.block(task.getId(), TaskBlockReason.CONCURRENCY_FULL);
            return;
        }

        ShardKey shardKey = shardingRouter.routeDialUnit(task.getTenantId(), task.getId());
        List<Long> ids = redisDialUnitQueue.claimReady(
                task.getTenantId(),
                task.getId(),
                shardKey.tableIndex(),
                granted,
                Instant.now().plus(properties.getProcessingTimeout())
        );
        if (ids.isEmpty()) {
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), granted);
            activeTaskQueue.block(task.getId(), TaskBlockReason.EMPTY);
            return;
        }

        if (ids.size() < granted) {
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), granted - ids.size());
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

        if (units.size() < ids.size()) {
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), ids.size() - units.size());
        }

        for (CallDialUnitEntity unit : units) {
            dialDispatchPublisher.publish(unit);
            metrics.incrementDispatchPublished();
        }

        if (units.isEmpty()) {
            activeTaskQueue.block(task.getId(), TaskBlockReason.EMPTY);
            return;
        }

        long nextFairScore = meta.get().fairScore() + ((long) units.size() * 1000 / meta.get().weight());
        if (units.size() < ids.size()) {
            activeTaskQueue.block(task.getId(), TaskBlockReason.EMPTY);
        } else if (granted < requested) {
            activeTaskQueue.block(task.getId(), TaskBlockReason.CONCURRENCY_FULL);
        } else {
            activeTaskQueue.reactivate(task.getId(), nextFairScore);
        }
    }
}
