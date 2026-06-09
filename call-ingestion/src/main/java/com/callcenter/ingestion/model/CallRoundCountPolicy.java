package com.callcenter.ingestion.model;

public final class CallRoundCountPolicy {

    private CallRoundCountPolicy() {
    }

    public static void validate(Long expectedRoundCount, long actualRoundCount, long callId) {
        if (expectedRoundCount == null) {
            return;
        }
        if (actualRoundCount != expectedRoundCount) {
            throw new IllegalStateException(
                    "call_round persisted count mismatch, callId=%d, expected=%d, actual=%d".formatted(
                            callId,
                            expectedRoundCount,
                            actualRoundCount
                    )
            );
        }
    }
}
