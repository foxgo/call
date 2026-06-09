package com.callcenter.ingestion.postprocess;

import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.ingestion.service.OutboxEventRepository;
import com.callcenter.ingestion.model.DomainEventMessage;
import com.callcenter.ingestion.model.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.model.CallRecordPersistedEvent;
import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.OutboxEventData;
import com.callcenter.ingestion.model.CallRecordMessage;
import com.callcenter.ingestion.model.CallRoundMessage;
import com.callcenter.ingestion.repository.CallRecordEntity;
import com.callcenter.ingestion.repository.CallRecordMapper;
import com.callcenter.ingestion.repository.MybatisCallRecordRepository;
import com.callcenter.ingestion.repository.CallRoundEntity;
import com.callcenter.ingestion.repository.CallRoundMapper;
import com.callcenter.ingestion.repository.MybatisCallRoundRepository;
import com.callcenter.ingestion.support.metrics.WriteMetrics;
import com.callcenter.ingestion.testsupport.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventFactoryTest {

    @Test
    void shouldBuildRecordPersistedEventWithStableEnvelopeAndPayload() throws Exception {
        OutboxEventFactory factory = new OutboxEventFactory(JsonSupport.objectMapper());
        CallRecordData entity = recordData();

        OutboxEventData outbox = factory.recordPersisted(CallRecordPersistedEvent.from(entity));
        DomainEventMessage event = JsonSupport.objectMapper().readValue(outbox.payload(), DomainEventMessage.class);

        assertThat(outbox.eventId()).isEqualTo("call_record_persisted:9:1001");
        assertThat(outbox.eventType()).isEqualTo("call_record_persisted");
        assertThat(outbox.aggregateType()).isEqualTo("CALL_RECORD");
        assertThat(outbox.aggregateId()).isEqualTo("1001");
        assertThat(outbox.tenantId()).isEqualTo(9L);
        assertThat(outbox.partitionKey()).isEqualTo("1001");
        assertThat(outbox.schemaVersion()).isEqualTo(1);
        assertThat(outbox.status()).isEqualTo("NEW");
        assertThat(event.eventId()).isEqualTo(outbox.eventId());
        assertThat(event.eventType()).isEqualTo(outbox.eventType());
        assertThat(event.aggregateType()).isEqualTo(outbox.aggregateType());
        assertThat(event.aggregateId()).isEqualTo(outbox.aggregateId());
        assertThat(event.tenantId()).isEqualTo(outbox.tenantId());
        assertThat(event.schemaVersion()).isEqualTo(1);

        JsonNode payload = event.payload();
        assertThat(payload.get("callId").asLong()).isEqualTo(1001L);
        assertThat(payload.get("tenantId").asLong()).isEqualTo(9L);
        assertThat(payload.get("phone").asText()).isEqualTo("13800138000");
        assertThat(payload.get("lineNumber").asText()).isEqualTo("021");
        assertThat(payload.get("callStatus").asInt()).isEqualTo(2);
        assertThat(payload.get("duration").asInt()).isEqualTo(180);
        assertThat(payload.get("roundTotal").asInt()).isEqualTo(3);
        assertThat(payload.get("recordingUrl").asText()).isEqualTo("https://cdn.example.com/recordings/1001.mp3");
        assertThat(payload.get("errorCode").asInt()).isEqualTo(1001);
        assertThat(payload.get("errorDescription").asText()).isEqualTo("callee busy");
        assertThat(payload.get("hangupBy").asInt()).isEqualTo(1);
        assertThat(payload.get("connected").asInt()).isEqualTo(1);
        assertThat(payload.get("ringDuration").asLong()).isEqualTo(1500L);
        assertThat(payload.get("ringStartTime").asText()).isEqualTo("2026-05-20T10:00:01.5");
        assertThat(payload.get("hangupTime").asText()).isEqualTo("2026-05-20T10:03:00.25");
        assertThat(payload.get("startTime").asText()).isEqualTo("2026-05-20T10:00:00");
        assertThat(payload.get("endTime").asText()).isEqualTo("2026-05-20T10:03:00");
    }

    @Test
    void shouldBuildAnalysisCompletedEventWithStableEnvelopeAndPayload() throws Exception {
        OutboxEventFactory factory = new OutboxEventFactory(JsonSupport.objectMapper());
        CallRecordData entity = recordData();

        OutboxEventData outbox = factory.analysisCompleted(CallAnalysisCompletedEvent.from(entity));
        DomainEventMessage event = JsonSupport.objectMapper().readValue(outbox.payload(), DomainEventMessage.class);

        assertThat(outbox.eventId()).isEqualTo("call_record_analysis_completed:9:1001");
        assertThat(outbox.eventType()).isEqualTo("call_record_analysis_completed");
        assertThat(outbox.aggregateType()).isEqualTo("CALL_RECORD");
        assertThat(outbox.aggregateId()).isEqualTo("1001");
        assertThat(outbox.tenantId()).isEqualTo(9L);
        assertThat(outbox.partitionKey()).isEqualTo("1001");
        assertThat(event.payload().get("callId").asLong()).isEqualTo(1001L);
        assertThat(event.payload().get("tenantId").asLong()).isEqualTo(9L);
        assertThat(event.payload().get("startTime").asText()).isEqualTo("2026-05-20T10:00:00");
    }

    @Test
    void shouldPersistRecordRowsAndOutboxRowsTogether() {
        CallRecordMapper callRecordMapper = mock(CallRecordMapper.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        WriteMetrics writeMetrics = mock(WriteMetrics.class);
        MybatisCallRecordRepository service = new MybatisCallRecordRepository(
                callRecordMapper,
                shardingRouter,
                outboxRepository,
                outboxEventFactory,
                writeMetrics
        );
        CallRecordMessage message = recordMessage();
        OutboxEventData outbox = new OutboxEventData(
                null,
                "call_record_persisted:9:1001",
                "call_record_persisted",
                "CALL_RECORD",
                "1001",
                9L,
                "1001",
                1,
                "{}",
                "NEW",
                0,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 10, 4),
                LocalDateTime.of(2026, 5, 20, 10, 4)
        );

        when(writeMetrics.mysqlInsertLatency()).thenReturn(mock(Timer.class));
        when(outboxEventFactory.recordPersisted(any(CallRecordPersistedEvent.class))).thenReturn(outbox);

        List<CallRecordEntity> entities = service.persistBatch(
                new ShardKey(9L, 0, 1, "202605"),
                List.of(message),
                ignored -> {
                }
        );

        assertThat(entities).singleElement().satisfies(entity -> {
            assertThat(entity.getCallId()).isEqualTo(1001L);
            assertThat(entity.getTenantId()).isEqualTo(9L);
            assertThat(entity.getPhone()).isEqualTo("13800138000");
            assertThat(entity.getRoundTotal()).isEqualTo(3);
            assertThat(entity.getRecordingUrl()).isEqualTo("https://cdn.example.com/recordings/1001.mp3");
            assertThat(entity.getErrorCode()).isEqualTo(1001);
            assertThat(entity.getErrorDescription()).isEqualTo("callee busy");
            assertThat(entity.getHangupBy()).isEqualTo((byte) 1);
            assertThat(entity.getConnected()).isEqualTo((byte) 1);
            assertThat(entity.getRingDuration()).isEqualTo(1500L);
            assertThat(entity.getRingStartTime()).isEqualTo(LocalDateTime.of(2026, 5, 20, 10, 0, 1, 500_000_000));
            assertThat(entity.getHangupTime()).isEqualTo(LocalDateTime.of(2026, 5, 20, 10, 3, 0, 250_000_000));
        });
        verify(callRecordMapper).batchInsertIgnore(entities);
        verify(outboxEventFactory).recordPersisted(any(CallRecordPersistedEvent.class));
        verify(outboxRepository).saveAll(List.of(outbox));
    }

    @Test
    void shouldNotInsertRecordOutboxWhenValidationFails() {
        CallRecordMapper callRecordMapper = mock(CallRecordMapper.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        WriteMetrics writeMetrics = mock(WriteMetrics.class);
        MybatisCallRecordRepository service = new MybatisCallRecordRepository(
                callRecordMapper,
                shardingRouter,
                outboxRepository,
                outboxEventFactory,
                writeMetrics
        );
        CallRecordMessage message = recordMessage();

        when(writeMetrics.mysqlInsertLatency()).thenReturn(mock(Timer.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.persistBatch(
                        new ShardKey(9L, 0, 1, "202605"),
                        List.of(message),
                        ignored -> {
                            throw new IllegalStateException("round mismatch");
                        }
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("round mismatch");

        verify(callRecordMapper).batchInsertIgnore(any());
        org.mockito.Mockito.verifyNoInteractions(outboxEventFactory, outboxRepository);
    }

    @Test
    void shouldPersistRoundRowsAndOutboxRowsTogether() {
        CallRoundMapper callRoundMapper = mock(CallRoundMapper.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        WriteMetrics writeMetrics = mock(WriteMetrics.class);
        MybatisCallRoundRepository service = new MybatisCallRoundRepository(
                callRoundMapper,
                shardingRouter,
                writeMetrics
        );
        CallRoundMessage message = new CallRoundMessage(77L, 9L, 1001L, 1, "AGENT", "hello", "GREETING", 1L);

        when(writeMetrics.mysqlInsertLatency()).thenReturn(mock(Timer.class));

        List<CallRoundEntity> entities = service.persistBatch(new ShardKey(9L, 0, 1, "202605"), List.of(message));

        assertThat(entities).singleElement().satisfies(entity -> {
            assertThat(entity.getRoundId()).isEqualTo(77L);
            assertThat(entity.getCallId()).isEqualTo(1001L);
            assertThat(entity.getTenantId()).isEqualTo(9L);
        });
        verify(callRoundMapper).batchInsertIgnore(entities);
    }

    private static CallRecordData recordData() {
        return new CallRecordData(
                1001L,
                9L,
                1L,
                "13800138000",
                "021",
                2,
                180,
                3,
                "https://cdn.example.com/recordings/1001.mp3",
                1001,
                "callee busy",
                (byte) 1,
                (byte) 1,
                1500L,
                LocalDateTime.of(2026, 5, 20, 10, 0, 1, 500_000_000),
                LocalDateTime.of(2026, 5, 20, 10, 3, 0, 250_000_000),
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 3),
                LocalDateTime.of(2026, 5, 20, 10, 4)
        );
    }

    private static CallRecordMessage recordMessage() {
        return new CallRecordMessage(
                1001L,
                9L,
                1L,
                "13800138000",
                "021",
                2,
                1L,
                2L,
                180,
                3,
                "https://cdn.example.com/recordings/1001.mp3",
                1001,
                "callee busy",
                (byte) 1,
                (byte) 1,
                1500L,
                1_779_271_201_500L,
                1_779_271_380_250L,
                null
        );
    }
}
