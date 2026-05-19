package com.callcenter.common.es;

import java.time.LocalDateTime;

public record CallRoundDocument(
        long tenantId,
        long callId,
        int roundIndex,
        String speaker,
        String content,
        String intent,
        LocalDateTime startTime
) {
}

