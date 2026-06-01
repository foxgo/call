package com.callcenter.task.dispatch.capacity;

import java.time.Instant;

public record CapacitySnapshot(
        String poolKey,
        int total,
        int busy,
        int idle,
        double utilization,
        double healthScore,
        Instant updatedAt
) {
}
