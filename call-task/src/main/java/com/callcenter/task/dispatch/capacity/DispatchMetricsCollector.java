package com.callcenter.task.dispatch.capacity;

import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.task.config.CallTaskCapacityControlProperties;
import com.callcenter.task.dispatch.DispatchConcurrencyLimiter;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
/**
 * 汇总容量控制所需的任务侧观测值。
 * 这些指标不会直接决定本轮下发多少，而是先参与 targetConcurrency 的重算。
 */
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
        // activeCalls 来自限流器中的 task in-flight 计数，表示当前已占用但尚未释放的下发额度。
        long activeCalls = concurrencyLimiter.currentTaskInFlight(tenantId, taskId);
        long successCount = metrics.writebackSuccessCount(taskId);
        long failureCount = metrics.writebackFailureCount(taskId);
        long completedCalls = successCount + failureCount;
        long remainingCalls = callDialUnitRepository.countRemainingDialUnits(shardKey, taskId);
        int currentTarget = taskTargetConcurrencyRegistry.loadTaskTarget(tenantId, taskId)
                .map(TaskTargetState::targetConcurrency)
                .orElse(0);
        // occupancy = 当前活跃通话数 / 当前任务目标并发，用于判断目标是否被充分吃满。
        double occupancy = currentTarget <= 0 ? 0.0d : Math.min(((double) activeCalls) / currentTarget, 1.0d);
        // connectRate 只看已完成写回的结果，避免把尚未回写的拨打算进分母。
        double rawConnectRate = completedCalls <= 0 ? 0.0d : ((double) successCount) / completedCalls;
        double smoothedConnectRate = connectRateEwma.compute(taskId, (ignored, previous) -> {
            if (previous == null) {
                return rawConnectRate;
            }
            // EWMA 用来平滑短周期抖动，默认让新值占 25%，历史值占 75%。
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
