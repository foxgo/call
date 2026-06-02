package com.callcenter.task.caller;

public record CallerIdCandidate(
        Long callerIdId,
        String callerId,
        String poolType,
        double costScore,
        double trustScore,
        int priorityBoost
) {
}
