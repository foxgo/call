package com.callcenter.task.dispatch.capacity;

public record ControlInput(
        DispatchMetricsSnapshot metrics,
        CapacitySnapshot capacity,
        TaskPolicy policy,
        int currentConcurrency,
        int currentTargetConcurrency
) {
}
