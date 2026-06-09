package com.callcenter.ingestion.model;

import java.time.LocalDateTime;

public record DeadLetterTaskData(
        String taskKey,
        String messageType,
        String sourceTopic,
        Integer sourcePartition,
        Long sourceOffset,
        String dlqTopic,
        Long dlqQueueOffset,
        String originMessageId,
        String messageKey,
        String idempotencyKey,
        String payloadType,
        String payload,
        String status,
        Integer dlqAttempt,
        Integer dlqMaxAttempts,
        LocalDateTime firstFailureAt,
        LocalDateTime lastFailureAt,
        String errorClass,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
