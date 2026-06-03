package com.callcenter.task.dispatch.capacity;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.enums.CallTaskStatus;
import com.callcenter.task.config.CallTaskCapacityControlProperties;
import com.callcenter.task.dispatch.ActiveTaskQueue;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.dispatch.TaskBlockReason;
import com.callcenter.task.dispatch.TaskSchedulingMeta;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/**
 * 容量控制定时任务。
 * 负责周期性重算每个运行中任务的目标并发，并把池级总量切分给具体任务。
 */
public class CapacityControlJob {

    private final ActiveTaskQueue activeTaskQueue;
    private final CallTaskRepository callTaskRepository;
    private final DispatchMetricsCollector dispatchMetricsCollector;
    private final CapacityProvider capacityProvider;
    private final CapacityControlEngine capacityControlEngine;
    private final TaskTargetAllocator taskTargetAllocator;
    private final TaskTargetConcurrencyRegistry taskTargetConcurrencyRegistry;
    private final TaskActivationService taskActivationService;
    private final CallTaskMetrics callTaskMetrics;
    private final CallTaskCapacityControlProperties properties;

    public CapacityControlJob(
            ActiveTaskQueue activeTaskQueue,
            CallTaskRepository callTaskRepository,
            DispatchMetricsCollector dispatchMetricsCollector,
            CapacityProvider capacityProvider,
            CapacityControlEngine capacityControlEngine,
            TaskTargetAllocator taskTargetAllocator,
            TaskTargetConcurrencyRegistry taskTargetConcurrencyRegistry,
            TaskActivationService taskActivationService,
            CallTaskMetrics callTaskMetrics,
            CallTaskCapacityControlProperties properties
    ) {
        this.activeTaskQueue = activeTaskQueue;
        this.callTaskRepository = callTaskRepository;
        this.dispatchMetricsCollector = dispatchMetricsCollector;
        this.capacityProvider = capacityProvider;
        this.capacityControlEngine = capacityControlEngine;
        this.taskTargetAllocator = taskTargetAllocator;
        this.taskTargetConcurrencyRegistry = taskTargetConcurrencyRegistry;
        this.taskActivationService = taskActivationService;
        this.callTaskMetrics = callTaskMetrics;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${call.task.capacity.control-interval:PT10S}")
    public void recalculateTargets() {
        CapacitySnapshot capacitySnapshot = capacityProvider.snapshot();
        Instant now = Instant.now();
        List<TaskTargetAllocationCandidate> candidates = new ArrayList<>();
        Map<Long, TaskSchedulingMeta> metaByTaskId = new HashMap<>();
        Map<Long, TaskTargetState> currentStateByTaskId = new HashMap<>();

        for (TaskSchedulingMeta meta : activeTaskQueue.listKnownMetas()) {
            CallTaskEntity task = callTaskRepository.findRequired(meta.tenantId(), meta.taskId());
            if (!CallTaskStatus.RUNNING.name().equals(task.getStatus())) {
                continue;
            }
            TaskTargetState currentState = taskTargetConcurrencyRegistry.loadTaskTarget(meta.tenantId(), meta.taskId()).orElse(null);
            currentStateByTaskId.put(meta.taskId(), currentState);

            DispatchMetricsSnapshot metrics = dispatchMetricsCollector.collectForTask(meta.tenantId(), meta.taskId());
            int currentTarget = currentState == null ? 0 : currentState.targetConcurrency();
            ControlInput input = new ControlInput(
                    metrics,
                    capacitySnapshot,
                    // 当前实现直接用任务静态 maxConcurrency 作为 baseConcurrency，
                    // 再由控制引擎结合接通率、占用率、池健康度等因子做乘法缩放。
                    new TaskPolicy(task.getMaxConcurrency(), properties.getTaskMinTarget(), task.getMaxConcurrency()),
                    (int) metrics.activeCalls(),
                    currentTarget
            );
            ControlDecision decision = capacityControlEngine.decide(input, currentState, now);
            callTaskMetrics.incrementCapacityDecision(decision.reason());
            // 这里的 decision.targetConcurrency 是“任务期望目标值”，
            // 后面还要经过池级分配器，最终才会变成真正写回 Redis 的 task target。
            candidates.add(new TaskTargetAllocationCandidate(meta.taskId(), meta.weight(), decision.targetConcurrency(), task.getMaxConcurrency()));
            metaByTaskId.put(meta.taskId(), meta);
        }

        // 当前单池模型下 poolTarget 直接等于容量池总量，后续限流器会把它当成池级硬约束。
        taskTargetConcurrencyRegistry.savePoolTarget(properties.getPoolKey(), capacitySnapshot.total());
        callTaskMetrics.updateCapacityPool(capacitySnapshot.total(), capacitySnapshot.busy(), capacitySnapshot.utilization());
        Map<Long, Integer> allocations = taskTargetAllocator.allocate(capacitySnapshot.total(), candidates);
        for (TaskTargetAllocationCandidate candidate : candidates) {
            // 如果 allocator 没给出结果，则退回到任务最小目标并发，避免任务永久拿不到额度。
            int targetConcurrency = allocations.getOrDefault(candidate.taskId(), properties.getTaskMinTarget());
            taskTargetConcurrencyRegistry.saveTaskTarget(
                    metaByTaskId.get(candidate.taskId()).tenantId(),
                    candidate.taskId(),
                    new TaskTargetState(
                            targetConcurrency,
                            now,
                            "pool_allocate",
                            now.plus(properties.getCooldown())
                    )
            );
            callTaskMetrics.incrementTaskTargetUpdated();
            TaskTargetState previous = currentStateByTaskId.get(candidate.taskId());
            TaskSchedulingMeta meta = metaByTaskId.get(candidate.taskId());
            if (meta != null
                    && meta.blockedReason() == TaskBlockReason.CONCURRENCY_FULL
                    && (previous == null || targetConcurrency > previous.targetConcurrency())) {
                // 任务之前因为并发额度不够被挂起，而本轮 target 变大了，需要主动唤醒重新参与调度。
                taskActivationService.activate(meta.tenantId(), meta.taskId());
            }
        }
    }
}
