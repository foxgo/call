package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.enums.CallTaskStatus;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.caller.CallerIdCandidate;
import com.callcenter.task.caller.CallerIdCandidateService;
import com.callcenter.task.caller.CallerIdSelection;
import com.callcenter.task.caller.CallerIdSelector;
import com.callcenter.task.caller.TaskCallerIdPolicy;
import com.callcenter.task.caller.TaskCallerIdPolicyService;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final AsyncDialDispatchService asyncDialDispatchService;
    private final TaskCallerIdPolicyService taskCallerIdPolicyService;
    private final CallerIdCandidateService callerIdCandidateService;
    private final CallerIdSelector callerIdSelector;
    private final CallTaskDispatchProperties properties;
    private final ShardingRouter shardingRouter;

    public PartitionSchedulerWorker(
            ActiveTaskQueue activeTaskQueue,
            CallTaskRepository callTaskRepository,
            DialUnitPreloadService dialUnitPreloadService,
            RedisDialUnitQueue redisDialUnitQueue,
            CallDialUnitRepository callDialUnitRepository,
            DispatchConcurrencyLimiter concurrencyLimiter,
            AsyncDialDispatchService asyncDialDispatchService,
            TaskCallerIdPolicyService taskCallerIdPolicyService,
            CallerIdCandidateService callerIdCandidateService,
            CallerIdSelector callerIdSelector,
            CallTaskDispatchProperties properties,
            ShardingRouter shardingRouter
    ) {
        this.activeTaskQueue = activeTaskQueue;
        this.callTaskRepository = callTaskRepository;
        this.dialUnitPreloadService = dialUnitPreloadService;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.callDialUnitRepository = callDialUnitRepository;
        this.concurrencyLimiter = concurrencyLimiter;
        this.asyncDialDispatchService = asyncDialDispatchService;
        this.taskCallerIdPolicyService = taskCallerIdPolicyService;
        this.callerIdCandidateService = callerIdCandidateService;
        this.callerIdSelector = callerIdSelector;
        this.properties = properties;
        this.shardingRouter = shardingRouter;
    }

    public boolean runPartition(int partition) {
        Optional<ActiveTaskQueue.ActiveTaskEntry> activeTask = activeTaskQueue.pollNextTaskWithMeta(partition);
        if (activeTask.isEmpty()) {
            return false;
        }

        TaskSchedulingMeta meta = activeTask.get().meta();
        Long taskId = activeTask.get().taskId();
        CallTaskEntity task = callTaskRepository.findRequired(meta.tenantId(), taskId);
        if (!CallTaskStatus.RUNNING.name().equals(task.getStatus())) {
            activeTaskQueue.block(meta, TaskBlockReason.PAUSED);
            return true;
        }
        dialUnitPreloadService.preloadRunningTask(task);

        int requested = Math.min(properties.getDispatchBatchSize(), task.getMaxConcurrency());
        int granted = concurrencyLimiter.tryAcquireBatch(task.getTenantId(), task.getId(), task.getMaxConcurrency(), requested);
        if (granted <= 0) {
            activeTaskQueue.block(meta, TaskBlockReason.CONCURRENCY_FULL);
            return true;
        }

        ShardKey shardKey = shardingRouter.routeDialUnit(task.getTenantId(), task.getId());
        Instant processingExpireAt = Instant.now().plus(properties.getProcessingTimeout());
        List<Long> ids = redisDialUnitQueue.claimReady(
                task.getTenantId(),
                task.getId(),
                shardKey.tableIndex(),
                granted,
                processingExpireAt
        );
        if (ids.isEmpty()) {
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), granted);
            activeTaskQueue.block(meta, TaskBlockReason.EMPTY);
            return true;
        }

        if (ids.size() < granted) {
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), granted - ids.size());
        }

        LocalDateTime now = LocalDateTime.now();
        List<CallDialUnitEntity> claimedUnits = callDialUnitRepository.listByTaskIdAndIds(shardKey, task.getId(), ids);
        TaskCallerIdPolicy policy = taskCallerIdPolicyService.toPolicy(task);
        List<CallerIdCandidate> candidates = callerIdCandidateService.listCandidates(
                task.getTenantId(),
                task.getId(),
                policy,
                now
        );
        List<CallDialUnitEntity> missingClaimedUnits = toMissedReadyUnits(ids, claimedUnits);
        List<CallDialUnitEntity> selectedUnits = new ArrayList<>();
        List<CallDialUnitEntity> rejectedUnits = new ArrayList<>();
        for (CallDialUnitEntity unit : claimedUnits) {
            Optional<CallerIdSelection> selection = callerIdSelector.select(
                    task.getTenantId(),
                    unit,
                    policy,
                    candidates
            );
            if (selection.isEmpty()) {
                CallDialUnitEntity rejected = new CallDialUnitEntity();
                rejected.setId(unit.getId());
                rejectedUnits.add(rejected);
                continue;
            }
            CallerIdSelection chosen = selection.get();
            unit.setDispatchToken(UUID.randomUUID().toString());
            unit.setSelectedCallerId(chosen.callerIdId());
            unit.setSelectedCallerNumber(chosen.callerId());
            unit.setCallerIdSelectionScore(chosen.score());
            unit.setCallerIdSelectionReason(chosen.reason());
            unit.setAttemptStage(chosen.attemptStage().name());
            selectedUnits.add(unit);
        }
        if (!missingClaimedUnits.isEmpty()) {
            redisDialUnitQueue.offerReady(task.getId(), shardKey.tableIndex(), missingClaimedUnits);
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), missingClaimedUnits.size());
        }
        if (!rejectedUnits.isEmpty()) {
            redisDialUnitQueue.offerReady(task.getId(), shardKey.tableIndex(), rejectedUnits);
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), rejectedUnits.size());
        }

        List<CallDialUnitEntity> units = callDialUnitRepository.markDialingSelectionsFromReady(
                shardKey,
                task.getId(),
                selectedUnits,
                now,
                now.plus(properties.getProcessingTimeout())
        );
        List<CallDialUnitEntity> missedUnits = toMissedReadyUnits(
                selectedUnits.stream().map(CallDialUnitEntity::getId).toList(),
                units
        );
        if (!missedUnits.isEmpty()) {
            redisDialUnitQueue.offerReady(task.getId(), shardKey.tableIndex(), missedUnits);
        }

        if (units.size() < selectedUnits.size()) {
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), selectedUnits.size() - units.size());
        }

        for (CallDialUnitEntity unit : units) {
            asyncDialDispatchService.submit(shardKey, unit);
        }

        if (units.isEmpty()) {
            activeTaskQueue.block(meta, TaskBlockReason.EMPTY);
            return true;
        }

        long nextFairScore = meta.fairScore() + ((long) units.size() * 1000 / meta.weight());
        if (units.size() < ids.size()) {
            activeTaskQueue.block(meta, TaskBlockReason.EMPTY);
        } else if (granted < requested) {
            activeTaskQueue.block(meta, TaskBlockReason.CONCURRENCY_FULL);
        } else {
            activeTaskQueue.reactivate(meta, nextFairScore);
        }
        return true;
    }

    private List<CallDialUnitEntity> toMissedReadyUnits(List<Long> claimedIds, List<CallDialUnitEntity> transitionedUnits) {
        Set<Long> transitionedIds = transitionedUnits.stream()
                .map(CallDialUnitEntity::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return claimedIds.stream()
                .filter(id -> !transitionedIds.contains(id))
                .map(id -> {
                    CallDialUnitEntity unit = new CallDialUnitEntity();
                    unit.setId(id);
                    return unit;
                })
                .toList();
    }
}
