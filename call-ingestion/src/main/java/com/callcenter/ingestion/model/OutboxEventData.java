package com.callcenter.ingestion.model;

import java.time.LocalDateTime;

public record OutboxEventData(
        Long id,
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long tenantId,
        String partitionKey,
        Integer schemaVersion,
        String payload,
        String status,
        Integer attemptCount,
        LocalDateTime nextAttemptAt,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
