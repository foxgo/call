package com.callcenter.task.dispatch.capacity;

public record TaskPolicy(
        int baseConcurrency,
        int minTarget,
        int maxConcurrency
) {
}
