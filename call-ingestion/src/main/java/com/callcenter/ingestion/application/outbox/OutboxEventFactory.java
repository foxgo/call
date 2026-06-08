package com.callcenter.ingestion.application.outbox;

import com.callcenter.ingestion.domain.event.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.domain.event.CallRecordPersistedEvent;
import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.domain.model.OutboxEventData;
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

    public OutboxEventData recordPersisted(CallRecordPersistedEvent event) {
        return createEvent(
                "call_record_persisted:%d:%d".formatted(event.tenantId(), event.callId()),
                "call_record_persisted",
                "CALL_RECORD",
                String.valueOf(event.callId()),
                event.tenantId(),
                String.valueOf(event.callId()),
                objectMapper.valueToTree(event)
        );
    }

    public OutboxEventData analysisCompleted(CallAnalysisCompletedEvent event) {
        return createEvent(
                "call_record_analysis_completed:%d:%d".formatted(event.tenantId(), event.callId()),
                "call_record_analysis_completed",
                "CALL_RECORD",
                String.valueOf(event.callId()),
                event.tenantId(),
                String.valueOf(event.callId()),
                objectMapper.valueToTree(event)
        );
    }

    private OutboxEventData createEvent(
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
        return new OutboxEventData(
                null,
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                tenantId,
                partitionKey,
                SCHEMA_VERSION,
                writeValue(envelope),
                STATUS_NEW,
                0,
                null,
                null,
                now,
                now
        );
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 outbox 事件失败", exception);
        }
    }
}
