package com.callcenter.ingestion.inbound.record;

import com.callcenter.ingestion.domain.record.CallRecordMessage;
import com.callcenter.ingestion.application.record.CallRecordIngestionService;
import com.callcenter.ingestion.domain.shared.InboundMessage;
import com.callcenter.ingestion.domain.shared.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqCallRecordConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDelegateRecordPayloadToRecordIngestionService() throws Exception {
        CallRecordIngestionService ingestionService = mock(CallRecordIngestionService.class);
        RocketMqCallRecordConsumer consumer = new RocketMqCallRecordConsumer(objectMapper, ingestionService);
        CallRecordMessage record = recordMessage();

        when(ingestionService.process(any())).thenReturn(true);

        consumer.onMessage(message(objectMapper.writeValueAsString(record)));

        ArgumentCaptor<InboundMessage<CallRecordMessage>> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(ingestionService).process(captor.capture());
        InboundMessage<CallRecordMessage> inbound = captor.getValue();
        assertEquals("9", inbound.messageKey());
        assertEquals(MessageType.RECORD, inbound.messageType());
        assertEquals("1001", inbound.idempotencyKey());
        assertEquals(record, inbound.payload());
    }

    @Test
    void shouldThrowWhenRetryableFailureNeedsRocketMqReconsume() throws Exception {
        CallRecordIngestionService ingestionService = mock(CallRecordIngestionService.class);
        RocketMqCallRecordConsumer consumer = new RocketMqCallRecordConsumer(objectMapper, ingestionService);
        CallRecordMessage record = recordMessage();

        when(ingestionService.process(any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message(objectMapper.writeValueAsString(record))));
    }

    private static MessageExt message(String payload) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return messageExt;
    }

    private static CallRecordMessage recordMessage() {
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
}
