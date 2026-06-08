package com.callcenter.ingestion.application.outbox;

import com.callcenter.ingestion.application.port.MessagePublisher;
import com.callcenter.ingestion.application.port.OutboxEventRepository;
import com.callcenter.ingestion.domain.model.OutboxEventData;
import com.callcenter.ingestion.infrastructure.outbox.OutboxPublisherProperties;
import com.callcenter.ingestion.infrastructure.config.PostprocessProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @Test
    void shouldClaimPendingRowsBeforePublishingAndDeleteThem() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        MessagePublisher messagePublisher = mock(MessagePublisher.class);
        OutboxPublisherProperties properties = properties();
        PostprocessProperties postprocessProperties = postprocessProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T06:00:00Z"), ZoneOffset.UTC);
        OutboxPublisher publisher = new OutboxPublisher(repository, messagePublisher, properties, postprocessProperties, clock);
        OutboxEventData recordEvent = claimedEvent(1L, "call_record_persisted", "1001");

        when(repository.claimPublishableBatch(any(), eq(20), eq(10))).thenReturn(List.of(recordEvent));

        publisher.publishPendingBatch();

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).recoverStaleProcessingRows(
                LocalDateTime.of(2026, 5, 20, 5, 55),
                LocalDateTime.of(2026, 5, 20, 6, 0)
        );
        inOrder.verify(repository).claimPublishableBatch(LocalDateTime.of(2026, 5, 20, 6, 0), 20, 10);
        verify(messagePublisher).publish("call_record_persisted", "1001", recordEvent.payload());
        verify(repository).deleteProcessingById(1L);
    }

    @Test
    void shouldIncrementAttemptAndScheduleRetryWhenClaimedPublishFails() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        MessagePublisher messagePublisher = mock(MessagePublisher.class);
        OutboxPublisherProperties properties = properties();
        PostprocessProperties postprocessProperties = postprocessProperties();
        Clock clock = steppingClock(
                Instant.parse("2026-05-20T06:00:00Z"),
                Instant.parse("2026-05-20T06:00:05Z")
        );
        OutboxPublisher publisher = new OutboxPublisher(repository, messagePublisher, properties, postprocessProperties, clock);
        OutboxEventData event = claimedEvent(9L, "call_record_persisted", "1001");

        when(repository.claimPublishableBatch(any(), eq(20), eq(10))).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(messagePublisher)
                .publish("call_record_persisted", "1001", event.payload());

        publisher.publishPendingBatch();

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).recoverStaleProcessingRows(
                LocalDateTime.of(2026, 5, 20, 5, 55),
                LocalDateTime.of(2026, 5, 20, 6, 0)
        );
        inOrder.verify(repository).claimPublishableBatch(LocalDateTime.of(2026, 5, 20, 6, 0), 20, 10);
        verify(repository).markFailed(
                eq(9L),
                eq(1),
                eq("java.lang.IllegalStateException: broker unavailable"),
                eq(LocalDateTime.of(2026, 5, 20, 6, 0, 5)),
                eq(LocalDateTime.of(2026, 5, 20, 6, 0, 35))
        );
    }

    @Test
    void shouldPublishAnalysisCompletedToConfiguredTopic() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        MessagePublisher messagePublisher = mock(MessagePublisher.class);
        OutboxPublisherProperties properties = properties();
        PostprocessProperties postprocessProperties = postprocessProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T06:00:00Z"), ZoneOffset.UTC);
        OutboxPublisher publisher = new OutboxPublisher(repository, messagePublisher, properties, postprocessProperties, clock);
        OutboxEventData event = claimedEvent(2L, "call_record_analysis_completed", "1001");

        when(repository.claimPublishableBatch(any(), eq(20), eq(10))).thenReturn(List.of(event));

        publisher.publishPendingBatch();

        verify(messagePublisher).publish("call_record_analysis_completed", "1001", event.payload());
        verify(repository).deleteProcessingById(2L);
    }

    private static OutboxPublisherProperties properties() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        properties.setBatchSize(20);
        properties.setProcessingTimeout(Duration.ofMinutes(5));
        properties.setRetryBackoff(Duration.ofSeconds(30));
        return properties;
    }

    private static PostprocessProperties postprocessProperties() {
        PostprocessProperties properties = new PostprocessProperties();
        properties.getTopics().setRecordPersisted("call_record_persisted");
        properties.getTopics().setAnalysisCompleted("call_record_analysis_completed");
        return properties;
    }

    private static OutboxEventData claimedEvent(Long id, String eventType, String partitionKey) {
        return new OutboxEventData(
                id,
                eventType + ":9:1001",
                eventType,
                "CALL_RECORD",
                "1001",
                9L,
                partitionKey,
                1,
                "{\"eventType\":\"%s\"}".formatted(eventType),
                "PROCESSING",
                0,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 6, 0),
                LocalDateTime.of(2026, 5, 20, 6, 0)
        );
    }

    private static Clock steppingClock(Instant... instants) {
        AtomicInteger index = new AtomicInteger();
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                int current = index.getAndUpdate(value -> Math.min(value + 1, instants.length - 1));
                return instants[current];
            }
        };
    }
}
