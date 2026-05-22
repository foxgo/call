package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventFactory {

    private static final int SCHEMA_VERSION = 1;
    private static final String STATUS_NEW = "NEW";

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CallEventOutboxEntity recordPersisted(CallRecordEntity entity) {
        return createEvent(
                "call_record_persisted:%d:%d".formatted(entity.getTenantId(), entity.getCallId()),
                "call_record_persisted",
                "CALL_RECORD",
                String.valueOf(entity.getCallId()),
                entity.getTenantId(),
                String.valueOf(entity.getCallId()),
                objectMapper.valueToTree(entity)
        );
    }

    private CallEventOutboxEntity createEvent(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            Long tenantId,
            String partitionKey,
            JsonNode payload
    ) {
        Instant occurredAt = Instant.now();
        LocalDateTime now = LocalDateTime.now();
        DomainEventMessage envelope = new DomainEventMessage(
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                tenantId,
                occurredAt,
                SCHEMA_VERSION,
                payload
        );
        CallEventOutboxEntity entity = new CallEventOutboxEntity();
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setTenantId(tenantId);
        entity.setPartitionKey(partitionKey);
        entity.setSchemaVersion(SCHEMA_VERSION);
        entity.setPayload(writeValue(envelope));
        entity.setStatus(STATUS_NEW);
        entity.setAttemptCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 outbox 事件失败", exception);
        }
    }
}
