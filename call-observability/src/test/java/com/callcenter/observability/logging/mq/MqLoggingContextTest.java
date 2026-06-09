package com.callcenter.observability.logging.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.callcenter.observability.logging.StructuredLogFields;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MqLoggingContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPopulateAndClearMqFields() {
        MessageExt messageExt = new MessageExt();
        messageExt.setTopic("call_record_ingest");
        messageExt.setMsgId("msg-1");
        messageExt.setKeys("key-1");
        messageExt.setQueueId(2);
        messageExt.setQueueOffset(12L);
        messageExt.setReconsumeTimes(3);

        MqLoggingContext context = new MqLoggingContext(messageExt);
        context.open();
        try {
            assertThat(MDC.get(StructuredLogFields.TOPIC)).isEqualTo("call_record_ingest");
            assertThat(MDC.get(StructuredLogFields.MESSAGE_ID)).isEqualTo("msg-1");
            assertThat(MDC.get(StructuredLogFields.MESSAGE_KEYS)).isEqualTo("key-1");
            assertThat(MDC.get(StructuredLogFields.QUEUE_ID)).isEqualTo("2");
            assertThat(MDC.get(StructuredLogFields.QUEUE_OFFSET)).isEqualTo("12");
            assertThat(MDC.get(StructuredLogFields.RECONSUME_TIMES)).isEqualTo("3");
        } finally {
            context.close();
        }

        assertThat(MDC.get(StructuredLogFields.TOPIC)).isNull();
        assertThat(MDC.get(StructuredLogFields.MESSAGE_ID)).isNull();
        assertThat(MDC.get(StructuredLogFields.MESSAGE_KEYS)).isNull();
        assertThat(MDC.get(StructuredLogFields.QUEUE_ID)).isNull();
        assertThat(MDC.get(StructuredLogFields.QUEUE_OFFSET)).isNull();
        assertThat(MDC.get(StructuredLogFields.RECONSUME_TIMES)).isNull();
    }
}
