package com.callcenter.task.dispatch.capacity;

import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskCapacityControlProperties;
import com.callcenter.task.dispatch.DispatchConcurrencyLimiter;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DispatchMetricsCollector {

    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final TaskTargetConcurrencyRegistry taskTargetConcurrencyRegistry;
    private final CapacityProvider capacityProvider;
    private final CallDialUnitRepository callDialUnitRepository;
    private final ShardingRouter shardingRouter;
    private final CallTaskMetrics metrics;
    private final CallTaskCapacityControlProperties properties;
    private final ConcurrentHashMap<Long, Double> connectRateEwma = new ConcurrentHashMap<>();

    public DispatchMetricsCollector(
            DispatchConcurrencyLimiter concurrencyLimiter,
            TaskTargetConcurrencyRegistry taskTargetConcurrencyRegistry,
            CapacityProvider capacityProvider,
            CallDialUnitRepository callDialUnitRepository,
            ShardingRouter shardingRouter,
            CallTaskMetrics metrics,
            CallTaskCapacityControlProperties properties
    ) {
        this.concurrencyLimiter = concurrencyLimiter;
        this.taskTargetConcurrencyRegistry = taskTargetConcurrencyRegistry;
        this.capacityProvider = capacityProvider;
        this.callDialUnitRepository = callDialUnitRepository;
        this.shardingRouter = shardingRouter;
        this.metrics = metrics;
        this.properties = properties;
    }

    public DispatchMetricsSnapshot collectForTask(Long tenantId, Long taskId) {
        CapacitySnapshot capacity = capacityProvider.snapshot();
        ShardKey shardKey = shardingRouter.routeDialUnit(tenantId, taskId);
        long activeCalls = concurrencyLimiter.currentTaskInFlight(taskId);
        long successCount = metrics.writebackSuccessCount(taskId);
        long failureCount = metrics.writebackFailureCount(taskId);
        long completedCalls = successCount + failureCount;
        long remainingCalls = callDialUnitRepository.countRemainingDialUnits(shardKey, taskId);
        int currentTarget = taskTargetConcurrencyRegistry.loadTaskTarget(taskId)
                .map(TaskTargetState::targetConcurrency)
                .orElse(0);
        double occupancy = currentTarget <= 0 ? 0.0d : Math.min(((double) activeCalls) / currentTarget, 1.0d);
        double rawConnectRate = completedCalls <= 0 ? 0.0d : ((double) successCount) / completedCalls;
        double smoothedConnectRate = connectRateEwma.compute(taskId, (ignored, previous) -> {
            if (previous == null) {
                return rawConnectRate;
            }
            return properties.getEwmaAlpha() * rawConnectRate + (1 - properties.getEwmaAlpha()) * previous;
        });
        return new DispatchMetricsSnapshot(
                taskId,
                smoothedConnectRate,
                occupancy,
                capacity.utilization(),
                capacity.healthScore(),
                capacity.utilization(),
                activeCalls,
                completedCalls,
                remainingCalls,
                Instant.now()
        );
    }
}
