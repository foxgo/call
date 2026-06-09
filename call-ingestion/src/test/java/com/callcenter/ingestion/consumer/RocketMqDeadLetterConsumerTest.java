package com.callcenter.ingestion.consumer;

import com.callcenter.ingestion.model.DomainEventMessage;
import com.callcenter.ingestion.model.CallRecordMessage;
import com.callcenter.ingestion.model.CallRoundMessage;
import com.callcenter.ingestion.service.DeadLetterTaskService;
import com.callcenter.ingestion.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RocketMqDeadLetterConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDelegateRecordDlqPayloadToTaskService() throws Exception {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqRecordDeadLetterConsumer consumer = new RocketMqRecordDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-record-consumer-group", objectMapper.writeValueAsString(recordPayload())));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.RECORD));
    }

    @Test
    void shouldDelegateRoundDlqPayloadToTaskService() throws Exception {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqRoundDeadLetterConsumer consumer = new RocketMqRoundDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-round-consumer-group", objectMapper.writeValueAsString(roundPayload())));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.ROUND));
    }

    @Test
    void shouldDelegateIndexDlqEnvelopeToTaskService() throws Exception {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqIndexDeadLetterConsumer consumer = new RocketMqIndexDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-index-group", objectMapper.writeValueAsString(envelope("call_record_persisted"))));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.INDEX));
    }

    @Test
    void shouldDelegateAiDlqEnvelopeToTaskService() throws Exception {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqAiDeadLetterConsumer consumer = new RocketMqAiDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-ai-group", objectMapper.writeValueAsString(envelope("call_record_persisted"))));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.AI));
    }

    @Test
    void shouldDelegateThirdPartyDlqEnvelopeToTaskService() throws Exception {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqThirdPartyDeadLetterConsumer consumer = new RocketMqThirdPartyDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-third-party-group", objectMapper.writeValueAsString(envelope("call_record_persisted"))));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.THIRD_PARTY));
    }

    @Test
    void shouldDelegateRawDeadLetterMessageToService() {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqRecordDeadLetterConsumer consumer = new RocketMqRecordDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-record-consumer-group", "{bad json}"));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.RECORD));
    }

    private CallRecordMessage recordPayload() {
        return new CallRecordMessage(
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
    }

    private CallRoundMessage roundPayload() {
        return new CallRoundMessage(77L, 9L, 1001L, 1, "AGENT", "hello", "GREETING", 1L);
    }

    private DomainEventMessage envelope(String eventType) {
        return new DomainEventMessage(
                "evt-1",
                eventType,
                "CALL",
                "1001",
                9L,
                Instant.parse("2026-05-25T08:00:00Z"),
                1,
                objectMapper.createObjectNode().put("callId", 1001L)
        );
    }

    private static MessageExt message(String topic, String payload) {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic(topic);
        messageExt.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return messageExt;
    }
}
