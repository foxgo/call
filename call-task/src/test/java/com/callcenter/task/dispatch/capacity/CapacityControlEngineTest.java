package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.config.CallTaskCapacityControlProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapacityControlEngineTest {

    @Test
    void shouldIncreaseTargetWithinMaxAdjustRatioWhenSignalsAreHealthy() {
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        CapacityControlEngine engine = new CapacityControlEngine(properties);

        ControlDecision decision = engine.decide(input(0.25d, 0.80d, 0.95d, 0.60d, 50, 50), null, Instant.parse("2026-06-01T00:00:00Z"));

        assertEquals(55, decision.targetConcurrency());
        assertTrue(decision.reason().contains("adjusted"));
    }

    @Test
    void shouldReduceTargetWhenHealthAndLoadSignalsArePoor() {
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        CapacityControlEngine engine = new CapacityControlEngine(properties);

        ControlDecision decision = engine.decide(input(0.05d, 0.95d, 0.60d, 0.90d, 50, 50), null, Instant.parse("2026-06-01T00:00:00Z"));

        assertEquals(45, decision.targetConcurrency());
        assertTrue(decision.reason().contains("adjusted"));
    }

    @Test
    void shouldIgnoreChangesInsideDeadband() {
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        CapacityControlEngine engine = new CapacityControlEngine(properties);

        ControlDecision decision = engine.decide(input(0.10d, 0.88d, 0.80d, 0.69d, 50, 50), null, Instant.parse("2026-06-01T00:00:00Z"));

        assertEquals(50, decision.targetConcurrency());
        assertTrue(decision.reason().contains("deadband"));
    }

    @Test
    void shouldKeepCurrentTargetDuringCooldown() {
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        CapacityControlEngine engine = new CapacityControlEngine(properties);
        TaskTargetState currentState = new TaskTargetState(
                50,
                Instant.parse("2026-06-01T00:00:00Z"),
                "steady",
                Instant.parse("2026-06-01T00:00:30Z")
        );

        ControlDecision decision = engine.decide(
                input(0.25d, 0.80d, 0.95d, 0.60d, 50, 50),
                currentState,
                Instant.parse("2026-06-01T00:00:10Z")
        );

        assertEquals(50, decision.targetConcurrency());
        assertTrue(decision.reason().contains("cooldown"));
    }

    private static ControlInput input(
            double connectRate,
            double occupancy,
            double trunkHealth,
            double llmLoad,
            int currentConcurrency,
            int currentTarget
    ) {
        DispatchMetricsSnapshot metrics = new DispatchMetricsSnapshot(
                1001L,
                connectRate,
                occupancy,
                0.25d,
                trunkHealth,
                llmLoad,
                currentConcurrency,
                10L,
                90L,
                Instant.parse("2026-06-01T00:00:00Z")
        );
        CapacitySnapshot capacity = new CapacitySnapshot("ai-default", 1000, 250, 750, 0.25d, trunkHealth, Instant.parse("2026-06-01T00:00:00Z"));
        return new ControlInput(metrics, capacity, new TaskPolicy(currentTarget, 1, 200), currentConcurrency, currentTarget);
    }
}
