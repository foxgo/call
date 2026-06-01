package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.config.CallTaskCapacityControlProperties;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class CapacityControlEngine {

    private final CallTaskCapacityControlProperties properties;

    public CapacityControlEngine(CallTaskCapacityControlProperties properties) {
        this.properties = properties;
    }

    public ControlDecision decide(ControlInput input, TaskTargetState currentState, Instant now) {
        int currentTarget = Math.max(input.currentTargetConcurrency(), 0);
        if (currentState != null && currentState.cooldownUntil() != null && now.isBefore(currentState.cooldownUntil())) {
            return new ControlDecision(currentTarget, "cooldown_active");
        }

        int baseConcurrency = resolveBaseConcurrency(input);
        double calculated = baseConcurrency
                * connectFactor(input.metrics().connectRate())
                * occupancyFactor(input.metrics().occupancy())
                * trunkHealthFactor(input.metrics().trunkHealth())
                * llmLoadFactor(input.metrics().llmLoad());

        int clamped = clampToStepLimit((int) Math.round(calculated), currentTarget);
        clamped = applyPolicyBounds(clamped, input.policy());

        if (isInsideDeadband(currentTarget, clamped)) {
            return new ControlDecision(currentTarget, "deadband_skip");
        }
        return new ControlDecision(clamped, "adjusted");
    }

    private int resolveBaseConcurrency(ControlInput input) {
        if (input.policy() != null && input.policy().baseConcurrency() > 0) {
            return input.policy().baseConcurrency();
        }
        return Math.max(Math.max(input.currentTargetConcurrency(), input.currentConcurrency()), properties.getTaskMinTarget());
    }

    private int clampToStepLimit(int calculated, int currentTarget) {
        int anchor = Math.max(currentTarget, properties.getTaskMinTarget());
        int lowerBound = (int) Math.ceil(anchor * (1.0d - properties.getMaxAdjustRatio()) - 1.0e-9);
        int upperBound = (int) Math.floor(anchor * (1.0d + properties.getMaxAdjustRatio()) + 1.0e-9);
        return Math.max(lowerBound, Math.min(upperBound, calculated));
    }

    private int applyPolicyBounds(int target, TaskPolicy policy) {
        int bounded = Math.max(target, properties.getTaskMinTarget());
        if (policy == null) {
            return bounded;
        }
        bounded = Math.max(bounded, policy.minTarget());
        return Math.min(bounded, policy.maxConcurrency());
    }

    private boolean isInsideDeadband(int currentTarget, int candidateTarget) {
        if (currentTarget <= 0) {
            return candidateTarget <= properties.getTaskMinTarget();
        }
        double changeRatio = Math.abs(candidateTarget - currentTarget) / (double) currentTarget;
        return changeRatio < properties.getDeadbandRatio();
    }

    private double connectFactor(double connectRate) {
        if (connectRate > 0.20d) {
            return 1.20d;
        }
        if (connectRate >= 0.08d) {
            return 1.00d;
        }
        return 0.80d;
    }

    private double occupancyFactor(double occupancy) {
        if (occupancy < 0.85d) {
            return 1.15d;
        }
        if (occupancy <= 0.92d) {
            return 1.00d;
        }
        return 0.85d;
    }

    private double trunkHealthFactor(double trunkHealth) {
        if (trunkHealth > 0.90d) {
            return 1.10d;
        }
        if (trunkHealth >= 0.70d) {
            return 1.00d;
        }
        return 0.75d;
    }

    private double llmLoadFactor(double llmLoad) {
        if (llmLoad < 0.70d) {
            return 1.00d;
        }
        if (llmLoad <= 0.85d) {
            return 0.90d;
        }
        return 0.80d;
    }
}
