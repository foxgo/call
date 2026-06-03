package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.config.CallTaskCapacityControlProperties;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
/**
 * 任务目标并发控制器。
 * 根据任务质量指标和池健康度，算出“下一轮建议的 targetConcurrency”。
 */
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
        // 核心公式：
        // target = baseConcurrency
        //        * 接通率因子
        //        * 目标占用率因子
        //        * 线路健康因子
        //        * 后处理负载因子
        double calculated = baseConcurrency
                * connectFactor(input.metrics().connectRate())
                * occupancyFactor(input.metrics().occupancy())
                * trunkHealthFactor(input.metrics().trunkHealth())
                * llmLoadFactor(input.metrics().llmLoad());

        // 单轮最大调整幅度受 maxAdjustRatio 限制，避免目标并发大起大落。
        int clamped = clampToStepLimit((int) Math.round(calculated), currentTarget);
        clamped = applyPolicyBounds(clamped, input.policy());

        if (isInsideDeadband(currentTarget, clamped)) {
            // 变化太小时保持原值，减少控制噪音和频繁唤醒。
            return new ControlDecision(currentTarget, "deadband_skip");
        }
        return new ControlDecision(clamped, "adjusted");
    }

    private int resolveBaseConcurrency(ControlInput input) {
        if (input.policy() != null && input.policy().baseConcurrency() > 0) {
            return input.policy().baseConcurrency();
        }
        // 没有显式 baseConcurrency 时，至少取当前目标、当前已占用并发和最小目标中的最大值。
        return Math.max(Math.max(input.currentTargetConcurrency(), input.currentConcurrency()), properties.getTaskMinTarget());
    }

    private int clampToStepLimit(int calculated, int currentTarget) {
        int anchor = Math.max(currentTarget, properties.getTaskMinTarget());
        // 例如 maxAdjustRatio=10% 时，100 这一轮最多只能调到 [90, 110]。
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
