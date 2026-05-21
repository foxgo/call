package com.callcenter.ingestion.mq;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqMessagePublisherTest {

    @Test
    void shouldPublishNormallyWithoutOrderlyRouting() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        RocketMqMessagePublisher publisher = new RocketMqMessagePublisher(rocketMQTemplate);

        publisher.publish("call_record_persisted", "1001", "{\"eventType\":\"call_record_persisted\"}");

        verify(rocketMQTemplate).syncSend("call_record_persisted", "{\"eventType\":\"call_record_persisted\"}");
        verify(rocketMQTemplate, never()).syncSendOrderly(anyString(), any(), anyString());
    }

    @Test
    void shouldPublishDelayedNormallyWithoutOrderlyQueueSelector() throws Exception {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        when(rocketMQTemplate.getProducer()).thenReturn(producer);
        RocketMqMessagePublisher publisher = new RocketMqMessagePublisher(rocketMQTemplate);

        publisher.publishDelayed("call_round_persisted", "1001", "{\"eventType\":\"call_round_persisted\"}", Duration.ofSeconds(5));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(messageCaptor.capture());
        verify(rocketMQTemplate, never()).getMessageQueueSelector();

        Message message = messageCaptor.getValue();
        assertThat(message.getTopic()).isEqualTo("call_round_persisted");
        assertThat(message.getKeys()).isEqualTo("1001");
        assertThat(message.getDelayTimeLevel()).isEqualTo(2);
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"eventType\":\"call_round_persisted\"}");
    }
}
