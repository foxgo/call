package com.callcenter.ingestion.domain.round;

public record CallRoundMessage(
        Long roundId,
        long tenantId,
        long callId,
        int roundIndex,
        String speaker,
        String content,
        String intent,
        Long startTime
) {
}
