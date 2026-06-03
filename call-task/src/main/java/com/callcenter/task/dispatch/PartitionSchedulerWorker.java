package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallCallerIdStatsEntity;
import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.enums.CallTaskStatus;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.caller.AttemptStage;
import com.callcenter.task.caller.CallerIdCandidate;
import com.callcenter.task.caller.CallerIdCandidateService;
import com.callcenter.task.caller.CallerIdSelection;
import com.callcenter.task.caller.CallerIdSelector;
import com.callcenter.task.caller.TaskCallerIdPolicy;
import com.callcenter.task.caller.TaskCallerIdPolicyService;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.repository.CallCallerIdStatsRepository;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 单个 partition 的核心调度执行器。
 * 职责包括：领取活跃任务、申请并发额度、从 Redis ready 队列 claim 号码、选择外显号并异步下发。
 */
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
    private final CallCallerIdStatsRepository callCallerIdStatsRepository;
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
            CallCallerIdStatsRepository callCallerIdStatsRepository,
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
        this.callCallerIdStatsRepository = callCallerIdStatsRepository;
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
        // 先把运行中任务的待拨数据预热到 Redis，避免后续 ready 队列被快速消费完后出现长时间饥饿。
        dialUnitPreloadService.preloadRunningTask(task);

        // requested 是本轮希望领取的下发额度上限，
        // 只受 dispatch batch size 和任务静态最大并发约束。
        int requested = Math.min(properties.getDispatchBatchSize(), task.getMaxConcurrency());
        // granted 是限流器批准的额度，已经综合了 global/pool/tenant/task/taskTarget 五层约束。
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
                granted,
                processingExpireAt
        );
        if (ids.isEmpty()) {
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), granted);
            activeTaskQueue.block(meta, TaskBlockReason.EMPTY);
            return true;
        }

        // ids.size() 是 ready 队列里本轮实际 claim 到的号码数，可能小于 granted。
        // 这一步之后，实际能继续往下走的上限就从 granted 缩减成了 ids.size()。
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
        Map<AttemptStage, Map<Long, CallCallerIdStatsEntity>> statsByStage = preloadStatsByStage(
                task.getTenantId(),
                claimedUnits,
                candidates
        );
        List<CallDialUnitEntity> missingClaimedUnits = toMissedReadyUnits(ids, claimedUnits);
        List<CallDialUnitEntity> selectedUnits = new ArrayList<>();
        List<CallDialUnitEntity> rejectedUnits = new ArrayList<>();
        for (CallDialUnitEntity unit : claimedUnits) {
            Optional<CallerIdSelection> selection = callerIdSelector.selectWithStats(
                    unit,
                    policy,
                    candidates,
                    statsByStage.getOrDefault(AttemptStage.fromRetryCount(unit.getRetryCount()), Map.of())
            );
            if (selection.isEmpty()) {
                // 当前号码在现有策略下没有可用外显号，重新放回 ready 队列等待下一轮调度。
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
            // Redis 已 claim，但数据库查不到记录，通常说明并发状态竞争或数据被其他流程修改，需要回补 ready 队列。
            redisDialUnitQueue.offerReady(task.getTenantId(), task.getId(), missingClaimedUnits);
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), missingClaimedUnits.size());
        }
        if (!rejectedUnits.isEmpty()) {
            redisDialUnitQueue.offerReady(task.getTenantId(), task.getId(), rejectedUnits);
            concurrencyLimiter.releaseBatch(task.getTenantId(), task.getId(), rejectedUnits.size());
        }

        List<CallDialUnitEntity> units = callDialUnitRepository.markDialingSelectionsFromReady(
                shardKey,
                task.getId(),
                selectedUnits,
                now,
                now.plus(properties.getProcessingTimeout())
        );
        // units.size() 才是最终真实下发数：
        // requested -> granted -> ids.size() -> selectedUnits.size() -> units.size()
        // 最后只有这些成功从 READY CAS 到 DIALING 的号码会被 submit。
        // 从 READY -> DIALING 的 CAS 更新可能只成功一部分，失败部分必须回补，否则会丢单。
        List<CallDialUnitEntity> missedUnits = toMissedReadyUnits(
                selectedUnits.stream().map(CallDialUnitEntity::getId).toList(),
                units
        );
        if (!missedUnits.isEmpty()) {
            redisDialUnitQueue.offerReady(task.getTenantId(), task.getId(), missedUnits);
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

        // 已分发量越大，fairScore 增长越快；权重越高，增长越慢，从而获得更多调度机会。
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

    private Map<AttemptStage, Map<Long, CallCallerIdStatsEntity>> preloadStatsByStage(
            Long tenantId,
            List<CallDialUnitEntity> units,
            List<CallerIdCandidate> candidates
    ) {
        if (units == null || units.isEmpty() || candidates == null || candidates.isEmpty()) {
            return Map.of();
        }
        List<Long> callerIds = candidates.stream()
                .map(CallerIdCandidate::callerIdId)
                .distinct()
                .toList();
        // 按尝试阶段分组预加载统计数据，避免每个号码单独查库造成 N+1。
        return units.stream()
                .map(unit -> AttemptStage.fromRetryCount(unit.getRetryCount()))
                .distinct()
                .collect(java.util.stream.Collectors.toMap(
                        stage -> stage,
                        stage -> callCallerIdStatsRepository.findLatestByCallerIds(tenantId, callerIds, stage.name())
                ));
    }

    private List<CallDialUnitEntity> toMissedReadyUnits(List<Long> claimedIds, List<CallDialUnitEntity> transitionedUnits) {
        // 这里只构造最小实体回补 Redis，避免为了回队列再补齐整行数据库字段。
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
