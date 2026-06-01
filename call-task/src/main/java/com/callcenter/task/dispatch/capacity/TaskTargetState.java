package com.callcenter.task.dispatch.capacity;

import java.time.Instant;

public record TaskTargetState(
        int targetConcurrency,
        Instant updatedAt,
        String reason,
        Instant cooldownUntil
) {
}
