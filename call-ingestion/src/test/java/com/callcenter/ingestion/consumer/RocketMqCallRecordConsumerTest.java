package com.callcenter.ingestion.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.callcenter.ingestion.model.CallRecordMessage;
import com.callcenter.ingestion.service.CallRecordIngestionService;
import com.callcenter.observability.logging.StructuredLogFields;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.mockito.Mockito;

class RocketMqCallRecordConsumerTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPopulateMqContextAroundMessageHandling() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CallRecordIngestionService service = Mockito.mock(CallRecordIngestionService.class);
        AtomicReference<String> topic = new AtomicReference<>();
        AtomicReference<String> messageId = new AtomicReference<>();
        when(service.process(any())).thenAnswer(invocation -> {
            topic.set(MDC.get(StructuredLogFields.TOPIC));
            messageId.set(MDC.get(StructuredLogFields.MESSAGE_ID));
            return true;
        });
        RocketMqCallRecordConsumer consumer = new RocketMqCallRecordConsumer(objectMapper, service);
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic("call_record_ingest");
        messageExt.setMsgId("msg-1001");
        messageExt.setBody(objectMapper.writeValueAsBytes(new CallRecordMessage(
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
        )));

        consumer.onMessage(messageExt);

        assertThat(topic).hasValue("call_record_ingest");
        assertThat(messageId).hasValue("msg-1001");
        assertThat(MDC.get(StructuredLogFields.TOPIC)).isNull();
        assertThat(MDC.get(StructuredLogFields.MESSAGE_ID)).isNull();
    }
}
