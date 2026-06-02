package com.callcenter.task.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CallTaskMetricsTest {

    @Test
    void shouldRegisterQueueAndWritebackCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new CallTaskMetrics(meterRegistry);

        assertNotNull(meterRegistry.find("call.task.dispatch.ready.size").gauge());
        assertNotNull(meterRegistry.find("call.task.writeback.success").counter());
        assertNotNull(meterRegistry.find("call.task.dispatch.send.failed").counter());
        assertNotNull(meterRegistry.find("call.task.dispatch.send.rejected").counter());
        assertNotNull(meterRegistry.find("call.task.dispatch.validation.failed").counter());
        assertNotNull(meterRegistry.find("call.task.dispatch.gate.rejected").counter());
        assertNotNull(meterRegistry.find("call.task.dispatch.compensated").counter());
        assertNotNull(meterRegistry.find("call.task.dispatch.compensation.skipped").counter());
    }

    @Test
    void shouldRegisterCapacityControlMetersAndUpdateTheirValues() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CallTaskMetrics metrics = new CallTaskMetrics(meterRegistry);

        metrics.updateCapacityPool(40, 10, 0.25d);
        metrics.incrementCapacityDecision("adjusted");
        metrics.incrementCapacityDecision("cooldown_active");
        metrics.incrementCapacityDecision("deadband_skip");
        metrics.incrementTaskTargetUpdated();

        assertNotNull(meterRegistry.find("call.task.capacity.pool.target").gauge());
        assertNotNull(meterRegistry.find("call.task.capacity.pool.busy").gauge());
        assertNotNull(meterRegistry.find("call.task.capacity.pool.utilization").gauge());
        assertNotNull(meterRegistry.find("call.task.capacity.control.decision").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.control.cooldown.skipped").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.control.deadband.skipped").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.task.target.updated").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.limit.global.rejected").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.limit.tenant.rejected").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.limit.pool.rejected").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.limit.task_static.rejected").counter());
        assertNotNull(meterRegistry.find("call.task.capacity.limit.task_target.rejected").counter());

        assertEquals(40.0d, meterRegistry.find("call.task.capacity.pool.target").gauge().value());
        assertEquals(10.0d, meterRegistry.find("call.task.capacity.pool.busy").gauge().value());
        assertEquals(0.25d, meterRegistry.find("call.task.capacity.pool.utilization").gauge().value());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.control.decision").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.control.cooldown.skipped").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.control.deadband.skipped").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.task.target.updated").counter().count());

        metrics.incrementCapacityReject("global");
        metrics.incrementCapacityReject("tenant");
        metrics.incrementCapacityReject("pool");
        metrics.incrementCapacityReject("taskStatic");
        metrics.incrementCapacityReject("taskTarget");

        assertEquals(1.0d, meterRegistry.find("call.task.capacity.limit.global.rejected").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.limit.tenant.rejected").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.limit.pool.rejected").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.limit.task_static.rejected").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.capacity.limit.task_target.rejected").counter().count());
    }

    @Test
    void shouldIncrementDispatchCompensationCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CallTaskMetrics metrics = new CallTaskMetrics(meterRegistry);

        metrics.incrementDispatchSendFailed();
        metrics.incrementDispatchSendRejected();
        metrics.incrementDispatchValidationFailed();
        metrics.incrementDispatchGateRejected();
        metrics.incrementDispatchCompensated();
        metrics.incrementDispatchCompensationSkipped();

        assertEquals(1.0d, meterRegistry.find("call.task.dispatch.send.failed").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.dispatch.send.rejected").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.dispatch.validation.failed").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.dispatch.gate.rejected").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.dispatch.compensated").counter().count());
        assertEquals(1.0d, meterRegistry.find("call.task.dispatch.compensation.skipped").counter().count());
    }
}
