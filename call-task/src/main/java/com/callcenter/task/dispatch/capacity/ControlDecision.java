package com.callcenter.task.dispatch.capacity;

public record ControlDecision(
        int targetConcurrency,
        String reason
) {
}
