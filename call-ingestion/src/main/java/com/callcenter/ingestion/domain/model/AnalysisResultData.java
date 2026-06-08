package com.callcenter.ingestion.domain.model;

import java.time.LocalDateTime;

public record AnalysisResultData(
        long tenantId,
        long callId,
        String status,
        String tags,
        Boolean riskFlag,
        Float qualityScore,
        String aiVersion,
        String errorMessage,
        LocalDateTime completedAt
) {
}
