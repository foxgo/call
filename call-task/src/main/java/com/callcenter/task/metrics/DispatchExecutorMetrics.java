package com.callcenter.task.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class DispatchExecutorMetrics {

    public DispatchExecutorMetrics(
            @Qualifier("callTaskDispatchSendExecutor") ThreadPoolTaskExecutor dispatchSendExecutor,
            MeterRegistry meterRegistry
    ) {
        Gauge.builder(
                "call.task.dispatch.send.executor.active",
                dispatchSendExecutor,
                ThreadPoolTaskExecutor::getActiveCount
        ).register(meterRegistry);
        Gauge.builder(
                "call.task.dispatch.send.executor.queue.size",
                dispatchSendExecutor,
                executor -> executor.getThreadPoolExecutor().getQueue().size()
        ).register(meterRegistry);
    }
}
