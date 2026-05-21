package com.callcenter.ingestion.outbox;

import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.ingestion.config.PostprocessProperties;
import com.callcenter.ingestion.mq.MessagePublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @Test
    void shouldPublishNewRowsToConfiguredPersistedTopicsAndMarkThemPublished() {
        OutboxRepository repository = mock(OutboxRepository.class);
        MessagePublisher messagePublisher = mock(MessagePublisher.class);
        OutboxPublisherProperties properties = properties();
        PostprocessProperties postprocessProperties = postprocessProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T06:00:00Z"), ZoneOffset.UTC);
        OutboxPublisher publisher = new OutboxPublisher(repository, messagePublisher, properties, postprocessProperties, clock);
        CallEventOutboxEntity recordEvent = event(1L, "call_record_persisted", "1001");
        CallEventOutboxEntity roundEvent = event(2L, "call_round_persisted", "1001");

        when(repository.findPublishableBatch(any(), eq(20))).thenReturn(List.of(recordEvent, roundEvent));

        publisher.publishPendingBatch();

        verify(messagePublisher).publish("call_record_persisted", "1001", recordEvent.getPayload());
        verify(messagePublisher).publish("call_round_persisted", "1001", roundEvent.getPayload());
        verify(repository).markPublished(1L, LocalDateTime.of(2026, 5, 20, 6, 0));
        verify(repository).markPublished(2L, LocalDateTime.of(2026, 5, 20, 6, 0));
    }

    @Test
    void shouldIncrementAttemptAndScheduleRetryWhenPublishFails() {
        OutboxRepository repository = mock(OutboxRepository.class);
        MessagePublisher messagePublisher = mock(MessagePublisher.class);
        OutboxPublisherProperties properties = properties();
        PostprocessProperties postprocessProperties = postprocessProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T06:00:00Z"), ZoneOffset.UTC);
        OutboxPublisher publisher = new OutboxPublisher(repository, messagePublisher, properties, postprocessProperties, clock);
        CallEventOutboxEntity event = event(9L, "call_record_persisted", "1001");

        when(repository.findPublishableBatch(any(), eq(20))).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(messagePublisher)
                .publish("call_record_persisted", "1001", event.getPayload());

        publisher.publishPendingBatch();

        verify(repository).markFailed(
                eq(9L),
                eq(1),
                eq("java.lang.IllegalStateException: broker unavailable"),
                eq(LocalDateTime.of(2026, 5, 20, 6, 0, 30))
        );
    }

    private static OutboxPublisherProperties properties() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        properties.setBatchSize(20);
        properties.setRetryBackoff(Duration.ofSeconds(30));
        return properties;
    }

    private static PostprocessProperties postprocessProperties() {
        PostprocessProperties properties = new PostprocessProperties();
        properties.getTopics().setRecordPersisted("call_record_persisted");
        properties.getTopics().setRoundPersisted("call_round_persisted");
        return properties;
    }

    private static CallEventOutboxEntity event(Long id, String eventType, String partitionKey) {
        CallEventOutboxEntity entity = new CallEventOutboxEntity();
        entity.setId(id);
        entity.setEventType(eventType);
        entity.setPayload("{\"eventType\":\"%s\"}".formatted(eventType));
        entity.setPartitionKey(partitionKey);
        entity.setAttemptCount(0);
        return entity;
    }
}
