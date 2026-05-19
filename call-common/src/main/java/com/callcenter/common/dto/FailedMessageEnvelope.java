package com.callcenter.common.dto;

public record FailedMessageEnvelope(
        String topic,
        String key,
        String payload,
        String errorMessage,
        int attempts
) {
}

