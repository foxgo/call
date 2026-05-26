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
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallTaskDispatcher {

    private final CallTaskRepository callTaskRepository;
    private final DialUnitPreloadService dialUnitPreloadService;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final CallDialUnitRepository callDialUnitRepository;
    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final DialDispatchPublisher dialDispatchPublisher;
    private final CallTaskDispatchProperties properties;
    private final ShardingRouter shardingRouter;
    private final CallTaskMetrics metrics;

    public CallTaskDispatcher(
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

    @Scheduled(fixedDelayString = "${call.task.dispatch.poll-interval:PT1S}")
    public void dispatchRunningTasks() {
        for (CallTaskEntity task : callTaskRepository.loadRunningTasks()) {
            if (!shouldDispatch(task)) {
                continue;
            }
            dialUnitPreloadService.preloadRunningTask(task);
            dispatchTask(task);
        }
    }

    private boolean shouldDispatch(CallTaskEntity task) {
        return task.getNextDispatchTime() == null || !task.getNextDispatchTime().isAfter(LocalDateTime.now());
    }

    private void dispatchTask(CallTaskEntity task) {
        ShardKey shardKey = shardingRouter.routeDialUnit(task.getTenantId(), task.getId());
        Instant expireAt = Instant.now().plus(properties.getProcessingTimeout());
        List<Long> ids = redisDialUnitQueue.claimReady(
                task.getId(),
                shardKey.tableIndex(),
                properties.getDispatchBatchSize(),
                expireAt
        );
        if (ids.isEmpty()) {
            pushNextDispatchTime(task);
            return;
        }

        List<Long> eligibleIds = new ArrayList<>();
        for (Long id : ids) {
            if (concurrencyLimiter.tryAcquire(task.getTenantId(), task.getId(), task.getMaxConcurrency())) {
                eligibleIds.add(id);
            } else {
                redisDialUnitQueue.returnReady(task.getId(), shardKey.tableIndex(), id, Instant.now().toEpochMilli());
            }
        }
        if (eligibleIds.isEmpty()) {
            pushNextDispatchTime(task);
            return;
        }

        String dispatchToken = UUID.randomUUID().toString();
        List<CallDialUnitEntity> units = callDialUnitRepository.markDialing(
                shardKey,
                task.getId(),
                eligibleIds,
                dispatchToken,
                LocalDateTime.now(),
                LocalDateTime.now().plus(properties.getProcessingTimeout())
        );

        Set<Long> markedIds = new HashSet<>(units.stream().map(CallDialUnitEntity::getId).toList());
        for (Long eligibleId : eligibleIds) {
            if (!markedIds.contains(eligibleId)) {
                concurrencyLimiter.release(task.getTenantId(), task.getId());
                redisDialUnitQueue.returnReady(task.getId(), shardKey.tableIndex(), eligibleId, Instant.now().toEpochMilli());
            }
        }

        for (CallDialUnitEntity unit : units) {
            try {
                dialDispatchPublisher.publish(unit);
                metrics.incrementDispatchPublished();
            } catch (RuntimeException exception) {
                concurrencyLimiter.release(task.getTenantId(), task.getId());
                callDialUnitRepository.revertDialingToQueued(
                        shardKey,
                        task.getId(),
                        unit.getId(),
                        unit.getDispatchToken(),
                        LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                );
                redisDialUnitQueue.returnReady(task.getId(), shardKey.tableIndex(), unit.getId(), Instant.now().toEpochMilli());
            }
        }
        pushNextDispatchTime(task);
    }

    private void pushNextDispatchTime(CallTaskEntity task) {
        task.setNextDispatchTime(LocalDateTime.now().plus(properties.getPollInterval()));
        task.setUpdatedAt(LocalDateTime.now());
        callTaskRepository.updateById(task);
    }
}
