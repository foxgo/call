package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.service.DeadLetterTaskService;
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
    void shouldDelegateRecordDlqEnvelopeToTaskService() throws Exception {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqRecordDeadLetterConsumer consumer = new RocketMqRecordDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-record-consumer-group", objectMapper.writeValueAsString(envelope("CALL_RECORD"))));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.RECORD));
    }

    @Test
    void shouldDelegateRoundDlqEnvelopeToTaskService() throws Exception {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqRoundDeadLetterConsumer consumer = new RocketMqRoundDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-round-consumer-group", objectMapper.writeValueAsString(envelope("CALL_ROUND"))));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.ROUND));
    }

    @Test
    void shouldDelegateRawDeadLetterMessageToService() {
        DeadLetterTaskService service = mock(DeadLetterTaskService.class);
        RocketMqRecordDeadLetterConsumer consumer = new RocketMqRecordDeadLetterConsumer(service);

        consumer.onMessage(message("%DLQ%call-record-consumer-group", "{bad json}"));

        verify(service).persist(org.mockito.ArgumentMatchers.any(MessageExt.class), org.mockito.ArgumentMatchers.eq(MessageType.RECORD));
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
