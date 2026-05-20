package com.callcenter.ingestion.config;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqListenerContainerCustomizerTest {

    @Test
    void shouldApplyConfiguredThreadMaxToRecordConsumerGroup() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getConsumers().getRecord().setGroup("call-record-consumer-group");
        properties.getConsumers().getRecord().setConsumeThreadMax(8);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-record-consumer-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqCallRecordConsumer");

        verify(consumer).setConsumeThreadMax(8);
    }

    @Test
    void shouldIgnoreUnknownConsumerGroup() {
        RocketMqProperties properties = new RocketMqProperties();
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("unknown-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "unknownConsumer");

        org.mockito.Mockito.verifyNoInteractions(consumer);
    }

    @Test
    void shouldApplyConfiguredThreadMaxToRoundConsumerGroup() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getConsumers().getRound().setGroup("call-round-consumer-group");
        properties.getConsumers().getRound().setConsumeThreadMax(5);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-round-consumer-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqCallRoundConsumer");

        verify(consumer).setConsumeThreadMax(5);
    }
}
