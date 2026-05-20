package com.callcenter.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record DomainEventMessage(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long tenantId,
        Instant occurredAt,
        int schemaVersion,
        JsonNode payload
) {
}
