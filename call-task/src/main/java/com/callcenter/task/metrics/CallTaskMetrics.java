package com.callcenter.task.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class CallTaskMetrics {

    private final AtomicLong readySize = new AtomicLong();
    private final AtomicLong processingSize = new AtomicLong();
    private final AtomicLong retrySize = new AtomicLong();
    private final Counter dispatchPublished;
    private final Counter writebackSuccess;
    private final Counter writebackFailure;
    private final Counter retryRequeued;
    private final Counter processingRecovered;

    public CallTaskMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("call.task.dispatch.ready.size", readySize, AtomicLong::get).register(meterRegistry);
        Gauge.builder("call.task.dispatch.processing.size", processingSize, AtomicLong::get).register(meterRegistry);
        Gauge.builder("call.task.dispatch.retry.size", retrySize, AtomicLong::get).register(meterRegistry);
        this.dispatchPublished = Counter.builder("call.task.dispatch.published").register(meterRegistry);
        this.writebackSuccess = Counter.builder("call.task.writeback.success").register(meterRegistry);
        this.writebackFailure = Counter.builder("call.task.writeback.failure").register(meterRegistry);
        this.retryRequeued = Counter.builder("call.task.retry.requeued").register(meterRegistry);
        this.processingRecovered = Counter.builder("call.task.processing.recovered").register(meterRegistry);
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

    public void incrementWritebackFailure() {
        writebackFailure.increment();
    }

    public void incrementRetryRequeued(long count) {
        retryRequeued.increment(count);
    }

    public void incrementProcessingRecovered(long count) {
        processingRecovered.increment(count);
    }
}
