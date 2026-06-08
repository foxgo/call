package com.callcenter.ingestion.domain.model;

import java.time.LocalDateTime;

public record CallRoundData(
        long roundId,
        long callId,
        long tenantId,
        int roundIndex,
        String speaker,
        String content,
        String intent,
        LocalDateTime startTime,
        LocalDateTime createdAt
) {
}
