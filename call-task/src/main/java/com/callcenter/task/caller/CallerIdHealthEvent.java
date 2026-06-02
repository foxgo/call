package com.callcenter.task.caller;

public record CallerIdHealthEvent(
        Long tenantId,
        Long callerIdId,
        AttemptStage attemptStage,
        boolean success,
        Integer ringDurationSeconds,
        Integer talkDurationSeconds,
        String failureCode
) {
}
