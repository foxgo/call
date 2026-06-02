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
                    new TaskPolicy(task.getMaxConcurrency(), properties.getTaskMinTarget(), task.getMaxConcurrency()),
                    (int) metrics.activeCalls(),
                    currentTarget
            );
            ControlDecision decision = capacityControlEngine.decide(input, currentState, now);
            callTaskMetrics.incrementCapacityDecision(decision.reason());
            candidates.add(new TaskTargetAllocationCandidate(meta.taskId(), meta.weight(), decision.targetConcurrency(), task.getMaxConcurrency()));
            metaByTaskId.put(meta.taskId(), meta);
        }

        taskTargetConcurrencyRegistry.savePoolTarget(properties.getPoolKey(), capacitySnapshot.total());
        callTaskMetrics.updateCapacityPool(capacitySnapshot.total(), capacitySnapshot.busy(), capacitySnapshot.utilization());
        Map<Long, Integer> allocations = taskTargetAllocator.allocate(capacitySnapshot.total(), candidates);
        for (TaskTargetAllocationCandidate candidate : candidates) {
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
                taskActivationService.activate(meta.tenantId(), meta.taskId());
            }
        }
    }
}
