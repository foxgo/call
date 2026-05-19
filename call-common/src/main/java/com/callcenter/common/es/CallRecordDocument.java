package com.callcenter.common.es;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CallRecordDocument(
        long tenantId,
        long callId,
        Long taskId,
        String phone,
        Integer callStatus,
        Integer duration,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String fullText,
        List<String> intents,
        List<String> tags,
        Boolean riskFlag,
        Float qualityScore,
        Integer roundCount,
        String aiVersion,
        Map<String, Object> ext,
        LocalDateTime createdAt
) {
}

