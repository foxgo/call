package com.callcenter.task.caller;

public record CallerIdSelection(
        Long callerIdId,
        String callerId,
        AttemptStage attemptStage,
        double score,
        String reason
) {
}
