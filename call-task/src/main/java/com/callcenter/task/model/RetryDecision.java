package com.callcenter.task.model;

import java.time.Instant;

public record RetryDecision(boolean processed, boolean shouldRetry, Instant retryAt) {

    public static RetryDecision stale() {
        return new RetryDecision(false, false, null);
    }

    public static RetryDecision noRetry() {
        return new RetryDecision(true, false, null);
    }

    public static RetryDecision retryAt(Instant retryAt) {
        return new RetryDecision(true, true, retryAt);
    }
}
