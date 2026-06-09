package com.callcenter.ingestion.service;

import com.callcenter.ingestion.service.DeadLetterTaskRepository;
import com.callcenter.ingestion.model.DomainEventMessage;
import com.callcenter.ingestion.model.DeadLetterTaskData;
import com.callcenter.ingestion.model.CallRecordMessage;
import com.callcenter.ingestion.model.CallRoundMessage;
import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.testsupport.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterTaskServiceTest {

    @Test
    void shouldPersistRecordDeadLetterTaskAsNew() throws Exception {
        DeadLetterTaskRepository repository = mock(DeadLetterTaskRepository.class);
        DeadLetterTaskService service = new DeadLetterTaskService(repository, JsonSupport.objectMapper());
        MessageExt messageExt = recordDeadLetterMessage();

        when(repository.insertIgnore(org.mockito.ArgumentMatchers.any(DeadLetterTaskData.class))).thenReturn(1);

        service.persist(messageExt, MessageType.RECORD);

        ArgumentCaptor<DeadLetterTaskData> captor = ArgumentCaptor.forClass(DeadLetterTaskData.class);
        verify(repository).insertIgnore(captor.capture());
        DeadLetterTaskData task = captor.getValue();
        assertThat(task.taskKey()).isEqualTo("%DLQ%call-record-consumer-group:origin-record-1");
        assertThat(task.messageType()).isEqualTo("RECORD");
        assertThat(task.sourceTopic()).isEqualTo("call_record_ingest");
        assertThat(task.sourcePartition()).isEqualTo(2);
        assertThat(task.sourceOffset()).isEqualTo(19L);
        assertThat(task.dlqTopic()).isEqualTo("%DLQ%call-record-consumer-group");
        assertThat(task.dlqQueueOffset()).isEqualTo(19L);
        assertThat(task.originMessageId()).isEqualTo("origin-record-1");
        assertThat(task.status()).isEqualTo("NEW");
        assertThat(task.dlqAttempt()).isEqualTo(4);
        assertThat(task.dlqMaxAttempts()).isEqualTo(6);
        assertThat(task.messageKey()).isEqualTo("1001");
        assertThat(task.payloadType()).isEqualTo("RECORD_INGEST");
        assertThat(task.payload()).contains("\"callId\":1001");
        assertThat(task.idempotencyKey()).isEqualTo("1001");
        assertThat(task.firstFailureAt()).isNull();
        assertThat(task.lastFailureAt()).isEqualTo(Instant.parse("2026-05-25T01:03:00Z").atOffset(java.time.ZoneOffset.UTC).toLocalDateTime());
    }

    @Test
    void shouldPersistRoundDeadLetterTaskAsNew() throws Exception {
        DeadLetterTaskRepository repository = mock(DeadLetterTaskRepository.class);
        DeadLetterTaskService service = new DeadLetterTaskService(repository, JsonSupport.objectMapper());
        MessageExt messageExt = roundDeadLetterMessage();

        when(repository.insertIgnore(org.mockito.ArgumentMatchers.any(DeadLetterTaskData.class))).thenReturn(1);

        service.persist(messageExt, MessageType.ROUND);

        ArgumentCaptor<DeadLetterTaskData> captor = ArgumentCaptor.forClass(DeadLetterTaskData.class);
        verify(repository).insertIgnore(captor.capture());
        DeadLetterTaskData task = captor.getValue();
        assertThat(task.taskKey()).isEqualTo("%DLQ%call-round-consumer-group:origin-round-1");
        assertThat(task.messageType()).isEqualTo("ROUND");
        assertThat(task.dlqTopic()).isEqualTo("%DLQ%call-round-consumer-group");
        assertThat(task.messageKey()).isEqualTo("1001:77");
        assertThat(task.payloadType()).isEqualTo("ROUND_INGEST");
        assertThat(task.idempotencyKey()).isEqualTo("1001:77");
        assertThat(task.payload()).contains("\"roundId\":77");
    }

    @Test
    void shouldPersistIndexDeadLetterTaskUsingEventIdAsIdempotencyKey() throws Exception {
        DeadLetterTaskRepository repository = mock(DeadLetterTaskRepository.class);
        DeadLetterTaskService service = new DeadLetterTaskService(repository, JsonSupport.objectMapper());
        MessageExt messageExt = postprocessDeadLetterMessage("%DLQ%call-index-group", "call_record_persisted", 31L, 2, "origin-index-1");

        when(repository.insertIgnore(org.mockito.ArgumentMatchers.any(DeadLetterTaskData.class))).thenReturn(1);

        service.persist(messageExt, MessageType.INDEX);

        ArgumentCaptor<DeadLetterTaskData> captor = ArgumentCaptor.forClass(DeadLetterTaskData.class);
        verify(repository).insertIgnore(captor.capture());
        DeadLetterTaskData task = captor.getValue();
        assertThat(task.messageType()).isEqualTo("INDEX");
        assertThat(task.payloadType()).isEqualTo("call_record_persisted");
        assertThat(task.idempotencyKey()).isEqualTo("evt-postprocess-1");
    }

    @Test
    void shouldPersistAiDeadLetterTaskUsingEventIdAsIdempotencyKey() throws Exception {
        DeadLetterTaskRepository repository = mock(DeadLetterTaskRepository.class);
        DeadLetterTaskService service = new DeadLetterTaskService(repository, JsonSupport.objectMapper());
        MessageExt messageExt = postprocessDeadLetterMessage("%DLQ%call-ai-group", "call_record_persisted", 33L, 3, "origin-ai-1");

        when(repository.insertIgnore(org.mockito.ArgumentMatchers.any(DeadLetterTaskData.class))).thenReturn(1);

        service.persist(messageExt, MessageType.AI);

        ArgumentCaptor<DeadLetterTaskData> captor = ArgumentCaptor.forClass(DeadLetterTaskData.class);
        verify(repository).insertIgnore(captor.capture());
        DeadLetterTaskData task = captor.getValue();
        assertThat(task.messageType()).isEqualTo("AI");
        assertThat(task.idempotencyKey()).isEqualTo("evt-postprocess-1");
    }

    @Test
    void shouldPersistThirdPartyDeadLetterTaskUsingEventIdAsIdempotencyKey() throws Exception {
        DeadLetterTaskRepository repository = mock(DeadLetterTaskRepository.class);
        DeadLetterTaskService service = new DeadLetterTaskService(repository, JsonSupport.objectMapper());
        MessageExt messageExt = postprocessDeadLetterMessage(
                "%DLQ%call-third-party-group",
                "call_record_analysis_completed",
                35L,
                4,
                "origin-third-party-1"
        );

        when(repository.insertIgnore(org.mockito.ArgumentMatchers.any(DeadLetterTaskData.class))).thenReturn(1);

        service.persist(messageExt, MessageType.THIRD_PARTY);

        ArgumentCaptor<DeadLetterTaskData> captor = ArgumentCaptor.forClass(DeadLetterTaskData.class);
        verify(repository).insertIgnore(captor.capture());
        DeadLetterTaskData task = captor.getValue();
        assertThat(task.messageType()).isEqualTo("THIRD_PARTY");
        assertThat(task.payloadType()).isEqualTo("call_record_analysis_completed");
        assertThat(task.idempotencyKey()).isEqualTo("evt-postprocess-1");
    }

    @Test
    void shouldTreatDuplicateDeadLetterTaskAsSuccess() throws Exception {
        DeadLetterTaskRepository repository = mock(DeadLetterTaskRepository.class);
        DeadLetterTaskService service = new DeadLetterTaskService(repository, JsonSupport.objectMapper());

        when(repository.insertIgnore(org.mockito.ArgumentMatchers.any(DeadLetterTaskData.class))).thenReturn(0);

        service.persist(recordDeadLetterMessage(), MessageType.RECORD);

        verify(repository).insertIgnore(org.mockito.ArgumentMatchers.any(DeadLetterTaskData.class));
    }

    @Test
    void shouldRejectMalformedAutoDeadLetterMessage() {
        DeadLetterTaskRepository repository = mock(DeadLetterTaskRepository.class);
        DeadLetterTaskService service = new DeadLetterTaskService(repository, JsonSupport.objectMapper());
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic("%DLQ%call-record-consumer-group");
        messageExt.setBody("{bad json}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.persist(messageExt, MessageType.RECORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法解析自动死信原始消息");
    }

    private MessageExt recordDeadLetterMessage() throws Exception {
        CallRecordMessage record = new CallRecordMessage(
                1001L,
                9L,
                1L,
                "13800138000",
                "021",
                1,
                1L,
                2L,
                3,
                2,
                "https://cdn.example.com/recordings/1001.mp3",
                1001,
                "callee busy",
                (byte) 1,
                (byte) 1,
                1500L,
                3L,
                4L,
                null
        );
        MessageExt message = messageExt("%DLQ%call-record-consumer-group", record, 19L, 4, "origin-record-1");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_RETRY_TOPIC, "call_record_ingest");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_REAL_QUEUE_ID, "2");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_MAX_RECONSUME_TIMES, "6");
        message.setStoreTimestamp(Instant.parse("2026-05-25T01:03:00Z").toEpochMilli());
        return message;
    }

    private MessageExt roundDeadLetterMessage() throws Exception {
        CallRoundMessage round = new CallRoundMessage(
                77L,
                9L,
                1001L,
                1,
                "AGENT",
                "hello",
                "GREETING",
                1747904400000L
        );
        MessageExt message = messageExt("%DLQ%call-round-consumer-group", round, 29L, 3, "origin-round-1");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_RETRY_TOPIC, "call_round_ingest");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_REAL_QUEUE_ID, "4");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_MAX_RECONSUME_TIMES, "5");
        message.setStoreTimestamp(Instant.parse("2026-05-25T02:01:00Z").toEpochMilli());
        return message;
    }

    private MessageExt postprocessDeadLetterMessage(
            String dlqTopic,
            String eventType,
            long queueOffset,
            int reconsumeTimes,
            String originMessageId
    ) throws Exception {
        DomainEventMessage event = domainEvent(
                "evt-postprocess-1",
                eventType,
                "CALL_RECORD",
                "1001",
                9L,
                JsonSupport.objectMapper().readTree("""
                        {"callId":1001,"tenantId":9,"taskId":1}
                        """)
        );
        MessageExt message = messageExt(dlqTopic, event, queueOffset, reconsumeTimes, originMessageId);
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_RETRY_TOPIC, "call_record_persisted");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_REAL_QUEUE_ID, "1");
        MessageAccessor.putProperty(message, MessageConst.PROPERTY_MAX_RECONSUME_TIMES, "7");
        message.setStoreTimestamp(Instant.parse("2026-05-25T03:01:00Z").toEpochMilli());
        return message;
    }

    private DomainEventMessage domainEvent(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            Long tenantId,
            JsonNode payload
    ) {
        return new DomainEventMessage(
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                tenantId,
                Instant.parse("2026-05-25T01:00:00Z"),
                1,
                payload
        );
    }

    private MessageExt messageExt(String topic, Object payload, long queueOffset, int reconsumeTimes, String originMessageId)
            throws Exception {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(topic);
        messageExt.setQueueOffset(queueOffset);
        messageExt.setReconsumeTimes(reconsumeTimes);
        messageExt.setMsgId("dlq-msg-" + originMessageId);
        messageExt.setBody(JsonSupport.objectMapper().writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
        MessageAccessor.setOriginMessageId(messageExt, originMessageId);
        return messageExt;
    }
}
