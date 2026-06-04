package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallDeadLetterTaskEntity;
import com.callcenter.common.mapper.CallDeadLetterTaskMapper;
import com.callcenter.ingestion.model.MessageType;
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
        CallDeadLetterTaskMapper mapper = mock(CallDeadLetterTaskMapper.class);
        DeadLetterTaskService service = new DeadLetterTaskService(mapper, JsonSupport.objectMapper());
        MessageExt messageExt = recordDeadLetterMessage();

        when(mapper.insertIgnore(org.mockito.ArgumentMatchers.any(CallDeadLetterTaskEntity.class))).thenReturn(1);

        service.persist(messageExt, MessageType.RECORD);

        ArgumentCaptor<CallDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(CallDeadLetterTaskEntity.class);
        verify(mapper).insertIgnore(captor.capture());
        CallDeadLetterTaskEntity task = captor.getValue();
        assertThat(task.getTaskKey()).isEqualTo("%DLQ%call-record-consumer-group:origin-record-1");
        assertThat(task.getMessageType()).isEqualTo("RECORD");
        assertThat(task.getSourceTopic()).isEqualTo("call_record_ingest");
        assertThat(task.getSourcePartition()).isEqualTo(2);
        assertThat(task.getSourceOffset()).isEqualTo(19L);
        assertThat(task.getDlqTopic()).isEqualTo("%DLQ%call-record-consumer-group");
        assertThat(task.getDlqQueueOffset()).isEqualTo(19L);
        assertThat(task.getOriginMessageId()).isEqualTo("origin-record-1");
        assertThat(task.getStatus()).isEqualTo("NEW");
        assertThat(task.getDlqAttempt()).isEqualTo(4);
        assertThat(task.getDlqMaxAttempts()).isEqualTo(6);
        assertThat(task.getMessageKey()).isEqualTo("1001");
        assertThat(task.getPayloadType()).isEqualTo("RECORD_INGEST");
        assertThat(task.getPayload()).contains("\"callId\":1001");
        assertThat(task.getIdempotencyKey()).isEqualTo("1001");
        assertThat(task.getFirstFailureAt()).isNull();
        assertThat(task.getLastFailureAt()).isEqualTo(Instant.parse("2026-05-25T01:03:00Z").atOffset(java.time.ZoneOffset.UTC).toLocalDateTime());
    }

    @Test
    void shouldPersistRoundDeadLetterTaskAsNew() throws Exception {
        CallDeadLetterTaskMapper mapper = mock(CallDeadLetterTaskMapper.class);
        DeadLetterTaskService service = new DeadLetterTaskService(mapper, JsonSupport.objectMapper());
        MessageExt messageExt = roundDeadLetterMessage();

        when(mapper.insertIgnore(org.mockito.ArgumentMatchers.any(CallDeadLetterTaskEntity.class))).thenReturn(1);

        service.persist(messageExt, MessageType.ROUND);

        ArgumentCaptor<CallDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(CallDeadLetterTaskEntity.class);
        verify(mapper).insertIgnore(captor.capture());
        CallDeadLetterTaskEntity task = captor.getValue();
        assertThat(task.getTaskKey()).isEqualTo("%DLQ%call-round-consumer-group:origin-round-1");
        assertThat(task.getMessageType()).isEqualTo("ROUND");
        assertThat(task.getDlqTopic()).isEqualTo("%DLQ%call-round-consumer-group");
        assertThat(task.getMessageKey()).isEqualTo("1001:77");
        assertThat(task.getPayloadType()).isEqualTo("ROUND_INGEST");
        assertThat(task.getIdempotencyKey()).isEqualTo("1001:77");
        assertThat(task.getPayload()).contains("\"roundId\":77");
    }

    @Test
    void shouldPersistIndexDeadLetterTaskUsingEventIdAsIdempotencyKey() throws Exception {
        CallDeadLetterTaskMapper mapper = mock(CallDeadLetterTaskMapper.class);
        DeadLetterTaskService service = new DeadLetterTaskService(mapper, JsonSupport.objectMapper());
        MessageExt messageExt = postprocessDeadLetterMessage("%DLQ%call-index-group", "call_record_persisted", 31L, 2, "origin-index-1");

        when(mapper.insertIgnore(org.mockito.ArgumentMatchers.any(CallDeadLetterTaskEntity.class))).thenReturn(1);

        service.persist(messageExt, MessageType.INDEX);

        ArgumentCaptor<CallDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(CallDeadLetterTaskEntity.class);
        verify(mapper).insertIgnore(captor.capture());
        CallDeadLetterTaskEntity task = captor.getValue();
        assertThat(task.getMessageType()).isEqualTo("INDEX");
        assertThat(task.getPayloadType()).isEqualTo("call_record_persisted");
        assertThat(task.getIdempotencyKey()).isEqualTo("evt-postprocess-1");
    }

    @Test
    void shouldPersistAiDeadLetterTaskUsingEventIdAsIdempotencyKey() throws Exception {
        CallDeadLetterTaskMapper mapper = mock(CallDeadLetterTaskMapper.class);
        DeadLetterTaskService service = new DeadLetterTaskService(mapper, JsonSupport.objectMapper());
        MessageExt messageExt = postprocessDeadLetterMessage("%DLQ%call-ai-group", "call_record_persisted", 33L, 3, "origin-ai-1");

        when(mapper.insertIgnore(org.mockito.ArgumentMatchers.any(CallDeadLetterTaskEntity.class))).thenReturn(1);

        service.persist(messageExt, MessageType.AI);

        ArgumentCaptor<CallDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(CallDeadLetterTaskEntity.class);
        verify(mapper).insertIgnore(captor.capture());
        CallDeadLetterTaskEntity task = captor.getValue();
        assertThat(task.getMessageType()).isEqualTo("AI");
        assertThat(task.getIdempotencyKey()).isEqualTo("evt-postprocess-1");
    }

    @Test
    void shouldPersistThirdPartyDeadLetterTaskUsingEventIdAsIdempotencyKey() throws Exception {
        CallDeadLetterTaskMapper mapper = mock(CallDeadLetterTaskMapper.class);
        DeadLetterTaskService service = new DeadLetterTaskService(mapper, JsonSupport.objectMapper());
        MessageExt messageExt = postprocessDeadLetterMessage(
                "%DLQ%call-third-party-group",
                "call_record_analysis_completed",
                35L,
                4,
                "origin-third-party-1"
        );

        when(mapper.insertIgnore(org.mockito.ArgumentMatchers.any(CallDeadLetterTaskEntity.class))).thenReturn(1);

        service.persist(messageExt, MessageType.THIRD_PARTY);

        ArgumentCaptor<CallDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(CallDeadLetterTaskEntity.class);
        verify(mapper).insertIgnore(captor.capture());
        CallDeadLetterTaskEntity task = captor.getValue();
        assertThat(task.getMessageType()).isEqualTo("THIRD_PARTY");
        assertThat(task.getPayloadType()).isEqualTo("call_record_analysis_completed");
        assertThat(task.getIdempotencyKey()).isEqualTo("evt-postprocess-1");
    }

    @Test
    void shouldTreatDuplicateDeadLetterTaskAsSuccess() throws Exception {
        CallDeadLetterTaskMapper mapper = mock(CallDeadLetterTaskMapper.class);
        DeadLetterTaskService service = new DeadLetterTaskService(mapper, JsonSupport.objectMapper());

        when(mapper.insertIgnore(org.mockito.ArgumentMatchers.any(CallDeadLetterTaskEntity.class))).thenReturn(0);

        service.persist(recordDeadLetterMessage(), MessageType.RECORD);

        verify(mapper).insertIgnore(org.mockito.ArgumentMatchers.any(CallDeadLetterTaskEntity.class));
    }

    @Test
    void shouldRejectMalformedAutoDeadLetterMessage() {
        CallDeadLetterTaskMapper mapper = mock(CallDeadLetterTaskMapper.class);
        DeadLetterTaskService service = new DeadLetterTaskService(mapper, JsonSupport.objectMapper());
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
