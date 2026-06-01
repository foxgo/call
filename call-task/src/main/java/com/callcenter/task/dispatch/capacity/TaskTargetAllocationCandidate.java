package com.callcenter.task.dispatch.capacity;

public record TaskTargetAllocationCandidate(
        Long taskId,
        int weight,
        int desiredTarget,
        int maxConcurrency
) {
}
