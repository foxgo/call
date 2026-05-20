package com.callcenter.common.dto;

public record RetryMessageEnvelope(
        String payload,
        String payloadType,
        String messageKey,
        String idempotencyKey,
        int attempt,
        int maxAttempts,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        long firstFailureAt,
        long lastFailureAt,
        String errorClass,
        String errorMessage
) {
}
