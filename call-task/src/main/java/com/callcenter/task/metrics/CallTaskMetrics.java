package com.callcenter.task.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class CallTaskMetrics {

    private final AtomicLong readySize = new AtomicLong();
    private final AtomicLong processingSize = new AtomicLong();
    private final AtomicLong retrySize = new AtomicLong();
    private final AtomicLong capacityPoolTarget = new AtomicLong();
    private final AtomicLong capacityPoolBusy = new AtomicLong();
    private final AtomicReference<Double> capacityPoolUtilization = new AtomicReference<>(0.0d);
    private final ConcurrentHashMap<Long, AtomicLong> taskWritebackSuccess = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> taskWritebackFailure = new ConcurrentHashMap<>();
    private final Counter dispatchPublished;
    private final Counter writebackSuccess;
    private final Counter writebackFailure;
    private final Counter retryRequeued;
    private final Counter processingRecovered;
    private final Counter capacityDecision;
    private final Counter capacityCooldownSkipped;
    private final Counter capacityDeadbandSkipped;
    private final Counter taskTargetUpdated;
    private final Counter capacityGlobalRejected;
    private final Counter capacityTenantRejected;
    private final Counter capacityPoolRejected;
    private final Counter capacityTaskStaticRejected;
    private final Counter capacityTaskTargetRejected;

    public CallTaskMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("call.task.dispatch.ready.size", readySize, AtomicLong::get).register(meterRegistry);
        Gauge.builder("call.task.dispatch.processing.size", processingSize, AtomicLong::get).register(meterRegistry);
        Gauge.builder("call.task.dispatch.retry.size", retrySize, AtomicLong::get).register(meterRegistry);
        Gauge.builder("call.task.capacity.pool.target", capacityPoolTarget, AtomicLong::get).register(meterRegistry);
        Gauge.builder("call.task.capacity.pool.busy", capacityPoolBusy, AtomicLong::get).register(meterRegistry);
        Gauge.builder("call.task.capacity.pool.utilization", capacityPoolUtilization, AtomicReference::get).register(meterRegistry);
        this.dispatchPublished = Counter.builder("call.task.dispatch.published").register(meterRegistry);
        this.writebackSuccess = Counter.builder("call.task.writeback.success").register(meterRegistry);
        this.writebackFailure = Counter.builder("call.task.writeback.failure").register(meterRegistry);
        this.retryRequeued = Counter.builder("call.task.retry.requeued").register(meterRegistry);
        this.processingRecovered = Counter.builder("call.task.processing.recovered").register(meterRegistry);
        this.capacityDecision = Counter.builder("call.task.capacity.control.decision").register(meterRegistry);
        this.capacityCooldownSkipped = Counter.builder("call.task.capacity.control.cooldown.skipped").register(meterRegistry);
        this.capacityDeadbandSkipped = Counter.builder("call.task.capacity.control.deadband.skipped").register(meterRegistry);
        this.taskTargetUpdated = Counter.builder("call.task.capacity.task.target.updated").register(meterRegistry);
        this.capacityGlobalRejected = Counter.builder("call.task.capacity.limit.global.rejected").register(meterRegistry);
        this.capacityTenantRejected = Counter.builder("call.task.capacity.limit.tenant.rejected").register(meterRegistry);
        this.capacityPoolRejected = Counter.builder("call.task.capacity.limit.pool.rejected").register(meterRegistry);
        this.capacityTaskStaticRejected = Counter.builder("call.task.capacity.limit.task_static.rejected").register(meterRegistry);
        this.capacityTaskTargetRejected = Counter.builder("call.task.capacity.limit.task_target.rejected").register(meterRegistry);
    }

    public void updateWindowSize(long ready, long processing, long retry) {
        readySize.set(ready);
        processingSize.set(processing);
        retrySize.set(retry);
    }

    public void incrementDispatchPublished() {
        dispatchPublished.increment();
    }

    public void incrementWritebackSuccess() {
        writebackSuccess.increment();
    }

    public void incrementWritebackSuccess(Long taskId) {
        incrementWritebackSuccess();
        taskWritebackSuccess.computeIfAbsent(taskId, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void incrementWritebackFailure() {
        writebackFailure.increment();
    }

    public void incrementWritebackFailure(Long taskId) {
        incrementWritebackFailure();
        taskWritebackFailure.computeIfAbsent(taskId, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void incrementRetryRequeued(long count) {
        retryRequeued.increment(count);
    }

    public void incrementProcessingRecovered(long count) {
        processingRecovered.increment(count);
    }

    public long writebackSuccessCount(Long taskId) {
        return taskWritebackSuccess.getOrDefault(taskId, new AtomicLong()).get();
    }

    public long writebackFailureCount(Long taskId) {
        return taskWritebackFailure.getOrDefault(taskId, new AtomicLong()).get();
    }

    public void updateCapacityPool(long target, long busy, double utilization) {
        capacityPoolTarget.set(target);
        capacityPoolBusy.set(busy);
        capacityPoolUtilization.set(utilization);
    }

    public void incrementCapacityDecision(String reason) {
        if ("cooldown_active".equals(reason)) {
            capacityCooldownSkipped.increment();
            return;
        }
        if ("deadband_skip".equals(reason)) {
            capacityDeadbandSkipped.increment();
            return;
        }
        capacityDecision.increment();
    }

    public void incrementTaskTargetUpdated() {
        taskTargetUpdated.increment();
    }

    public void incrementCapacityReject(String scope) {
        if ("global".equals(scope)) {
            capacityGlobalRejected.increment();
            return;
        }
        if ("tenant".equals(scope)) {
            capacityTenantRejected.increment();
            return;
        }
        if ("pool".equals(scope)) {
            capacityPoolRejected.increment();
            return;
        }
        if ("taskStatic".equals(scope)) {
            capacityTaskStaticRejected.increment();
            return;
        }
        if ("taskTarget".equals(scope)) {
            capacityTaskTargetRejected.increment();
        }
    }
}
