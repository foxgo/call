package com.callcenter.task.dispatch.capacity;

import java.time.Instant;

public record DispatchMetricsSnapshot(
        long taskId,
        double connectRate,
        double occupancy,
        double poolUtilization,
        double trunkHealth,
        double llmLoad,
        long activeCalls,
        long completedCalls,
        long remainingCalls,
        Instant timestamp
) {
}
