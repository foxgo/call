package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.mapper.CallEventOutboxMapper;
import com.callcenter.common.mapper.CallRecordMapper;
import com.callcenter.common.mapper.CallRoundMapper;
import com.callcenter.common.route.ShardKey;
import com.callcenter.ingestion.config.WriteMetrics;
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
        CallRecordEntity entity = recordEntity();

        CallEventOutboxEntity outbox = factory.recordPersisted(entity);
        DomainEventMessage event = JsonSupport.objectMapper().readValue(outbox.getPayload(), DomainEventMessage.class);

        assertThat(outbox.getEventId()).isEqualTo("call_record_persisted:9:1001");
        assertThat(outbox.getEventType()).isEqualTo("call_record_persisted");
        assertThat(outbox.getAggregateType()).isEqualTo("CALL_RECORD");
        assertThat(outbox.getAggregateId()).isEqualTo("1001");
        assertThat(outbox.getTenantId()).isEqualTo(9L);
        assertThat(outbox.getPartitionKey()).isEqualTo("1001");
        assertThat(outbox.getSchemaVersion()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo("NEW");
        assertThat(event.eventId()).isEqualTo(outbox.getEventId());
        assertThat(event.eventType()).isEqualTo(outbox.getEventType());
        assertThat(event.aggregateType()).isEqualTo(outbox.getAggregateType());
        assertThat(event.aggregateId()).isEqualTo(outbox.getAggregateId());
        assertThat(event.tenantId()).isEqualTo(outbox.getTenantId());
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
        CallRecordEntity entity = recordEntity();

        CallEventOutboxEntity outbox = factory.analysisCompleted(entity);
        DomainEventMessage event = JsonSupport.objectMapper().readValue(outbox.getPayload(), DomainEventMessage.class);

        assertThat(outbox.getEventId()).isEqualTo("call_record_analysis_completed:9:1001");
        assertThat(outbox.getEventType()).isEqualTo("call_record_analysis_completed");
        assertThat(outbox.getAggregateType()).isEqualTo("CALL_RECORD");
        assertThat(outbox.getAggregateId()).isEqualTo("1001");
        assertThat(outbox.getTenantId()).isEqualTo(9L);
        assertThat(outbox.getPartitionKey()).isEqualTo("1001");
        assertThat(event.payload().get("callId").asLong()).isEqualTo(1001L);
        assertThat(event.payload().get("tenantId").asLong()).isEqualTo(9L);
        assertThat(event.payload().get("startTime").asText()).isEqualTo("2026-05-20T10:00:00");
    }

    @Test
    void shouldPersistRecordRowsAndOutboxRowsTogether() {
        CallRecordMapper callRecordMapper = mock(CallRecordMapper.class);
        CallEventOutboxMapper outboxMapper = mock(CallEventOutboxMapper.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        WriteMetrics writeMetrics = mock(WriteMetrics.class);
        CallRecordMysqlService service = new CallRecordMysqlService(
                callRecordMapper,
                outboxMapper,
                outboxEventFactory,
                writeMetrics
        );
        CallRecordMessage message = recordMessage();
        CallEventOutboxEntity outbox = new CallEventOutboxEntity();

        when(writeMetrics.mysqlInsertLatency()).thenReturn(mock(Timer.class));
        when(outboxEventFactory.recordPersisted(any())).thenReturn(outbox);

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
        verify(outboxEventFactory).recordPersisted(entities.getFirst());
        verify(outboxMapper).batchInsert(List.of(outbox));
    }

    @Test
    void shouldNotInsertRecordOutboxWhenValidationFails() {
        CallRecordMapper callRecordMapper = mock(CallRecordMapper.class);
        CallEventOutboxMapper outboxMapper = mock(CallEventOutboxMapper.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        WriteMetrics writeMetrics = mock(WriteMetrics.class);
        CallRecordMysqlService service = new CallRecordMysqlService(
                callRecordMapper,
                outboxMapper,
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
        org.mockito.Mockito.verifyNoInteractions(outboxEventFactory, outboxMapper);
    }

    @Test
    void shouldPersistRoundRowsAndOutboxRowsTogether() {
        CallRoundMapper callRoundMapper = mock(CallRoundMapper.class);
        WriteMetrics writeMetrics = mock(WriteMetrics.class);
        CallRoundMysqlService service = new CallRoundMysqlService(
                callRoundMapper,
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

    private static CallRecordEntity recordEntity() {
        CallRecordEntity entity = new CallRecordEntity();
        entity.setCallId(1001L);
        entity.setTenantId(9L);
        entity.setTaskId(1L);
        entity.setPhone("13800138000");
        entity.setLineNumber("021");
        entity.setCallStatus(2);
        entity.setDuration(180);
        entity.setRoundTotal(3);
        entity.setRecordingUrl("https://cdn.example.com/recordings/1001.mp3");
        entity.setErrorCode(1001);
        entity.setErrorDescription("callee busy");
        entity.setHangupBy((byte) 1);
        entity.setConnected((byte) 1);
        entity.setRingDuration(1500L);
        entity.setRingStartTime(LocalDateTime.of(2026, 5, 20, 10, 0, 1, 500_000_000));
        entity.setHangupTime(LocalDateTime.of(2026, 5, 20, 10, 3, 0, 250_000_000));
        entity.setStartTime(LocalDateTime.of(2026, 5, 20, 10, 0));
        entity.setEndTime(LocalDateTime.of(2026, 5, 20, 10, 3));
        entity.setCreatedAt(LocalDateTime.of(2026, 5, 20, 10, 4));
        return entity;
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
