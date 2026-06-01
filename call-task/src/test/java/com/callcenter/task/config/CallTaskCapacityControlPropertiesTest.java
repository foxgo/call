package com.callcenter.task.config;

import com.callcenter.task.dispatch.capacity.CapacitySnapshot;
import com.callcenter.task.dispatch.capacity.ControlDecision;
import com.callcenter.task.dispatch.capacity.ControlInput;
import com.callcenter.task.dispatch.capacity.DispatchMetricsSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallTaskCapacityControlPropertiesTest {

    @Test
    void shouldBindCapacityControlProperties() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "call.task.capacity.control-interval", "PT10S",
                "call.task.capacity.metrics-interval", "PT5S",
                "call.task.capacity.cooldown", "PT30S",
                "call.task.capacity.deadband-ratio", "0.05",
                "call.task.capacity.max-adjust-ratio", "0.10",
                "call.task.capacity.pool-key", "ai-default",
                "call.task.capacity.pool-hard-max", "1000",
                "call.task.capacity.task-min-target", "1",
                "call.task.capacity.task-base-share", "2",
                "call.task.capacity.ewma-alpha", "0.25"
        )));

        CallTaskCapacityControlProperties properties = binder
                .bind("call.task.capacity", CallTaskCapacityControlProperties.class)
                .orElseThrow(() -> new AssertionError("capacity properties should bind"));

        assertEquals(Duration.ofSeconds(10), properties.getControlInterval());
        assertEquals(Duration.ofSeconds(5), properties.getMetricsInterval());
        assertEquals(Duration.ofSeconds(30), properties.getCooldown());
        assertEquals(0.05d, properties.getDeadbandRatio());
        assertEquals(0.10d, properties.getMaxAdjustRatio());
        assertEquals("ai-default", properties.getPoolKey());
        assertEquals(1000, properties.getPoolHardMax());
        assertEquals(1, properties.getTaskMinTarget());
        assertEquals(2, properties.getTaskBaseShare());
        assertEquals(0.25d, properties.getEwmaAlpha());
    }

    @Test
    void shouldExposeCapacityControlRecords() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        CapacitySnapshot capacity = new CapacitySnapshot("ai-default", 1000, 250, 750, 0.25d, 0.90d, now);
        DispatchMetricsSnapshot metrics = new DispatchMetricsSnapshot(
                1001L,
                0.12d,
                0.88d,
                0.25d,
                0.93d,
                0.70d,
                12L,
                30L,
                88L,
                now
        );
        ControlInput input = new ControlInput(metrics, capacity, null, 12, 16);
        ControlDecision decision = new ControlDecision(18, "steady");

        assertEquals("ai-default", capacity.poolKey());
        assertEquals(1001L, metrics.taskId());
        assertEquals(16, input.currentTargetConcurrency());
        assertEquals(18, decision.targetConcurrency());
        assertEquals("steady", decision.reason());
    }
}
