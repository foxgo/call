package com.callcenter.task.caller;

public enum AttemptStage {
    FIRST_ATTEMPT,
    RETRY_ATTEMPT;

    public static AttemptStage fromRetryCount(Integer retryCount) {
        return retryCount == null || retryCount <= 0 ? FIRST_ATTEMPT : RETRY_ATTEMPT;
    }
}
