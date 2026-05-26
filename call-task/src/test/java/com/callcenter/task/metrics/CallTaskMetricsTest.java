package com.callcenter.task.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CallTaskMetricsTest {

    @Test
    void shouldRegisterQueueAndWritebackCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new CallTaskMetrics(meterRegistry);

        assertNotNull(meterRegistry.find("call.task.dispatch.ready.size").gauge());
        assertNotNull(meterRegistry.find("call.task.writeback.success").counter());
    }
}
