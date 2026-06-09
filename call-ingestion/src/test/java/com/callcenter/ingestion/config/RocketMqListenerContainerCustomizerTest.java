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
        properties.getConsumers().getRecord().setMaxReconsumeTimes(4);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-record-consumer-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqCallRecordConsumer");

        verify(consumer).setConsumeThreadMax(8);
        verify(consumer).setMaxReconsumeTimes(4);
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
        properties.getConsumers().getRound().setMaxReconsumeTimes(6);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-round-consumer-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqCallRoundConsumer");

        verify(consumer).setConsumeThreadMax(5);
        verify(consumer).setMaxReconsumeTimes(6);
    }

    @Test
    void shouldApplyConfiguredThreadMaxToRecordDeadLetterConsumerGroup() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getConsumers().getRecordDlq().setGroup("call-record-dlq-group");
        properties.getConsumers().getRecordDlq().setConsumeThreadMax(2);
        properties.getConsumers().getRecordDlq().setMaxReconsumeTimes(7);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-record-dlq-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqRecordDeadLetterConsumer");

        verify(consumer).setConsumeThreadMax(2);
        verify(consumer).setMaxReconsumeTimes(7);
    }

    @Test
    void shouldApplyConfiguredThreadMaxToIndexDeadLetterConsumerGroup() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getConsumers().getIndexDlq().setGroup("call-index-dlq-group");
        properties.getConsumers().getIndexDlq().setConsumeThreadMax(3);
        properties.getConsumers().getIndexDlq().setMaxReconsumeTimes(8);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-index-dlq-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqIndexDeadLetterConsumer");

        verify(consumer).setConsumeThreadMax(3);
        verify(consumer).setMaxReconsumeTimes(8);
    }

    @Test
    void shouldApplyConfiguredThreadMaxToAiDeadLetterConsumerGroup() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getConsumers().getAiDlq().setGroup("call-ai-dlq-group");
        properties.getConsumers().getAiDlq().setConsumeThreadMax(1);
        properties.getConsumers().getAiDlq().setMaxReconsumeTimes(9);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-ai-dlq-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqAiDeadLetterConsumer");

        verify(consumer).setConsumeThreadMax(1);
        verify(consumer).setMaxReconsumeTimes(9);
    }

    @Test
    void shouldApplyConfiguredThreadMaxToThirdPartyDeadLetterConsumerGroup() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getConsumers().getThirdPartyDlq().setGroup("call-third-party-dlq-group");
        properties.getConsumers().getThirdPartyDlq().setConsumeThreadMax(2);
        properties.getConsumers().getThirdPartyDlq().setMaxReconsumeTimes(10);
        RocketMqListenerContainerCustomizer customizer = new RocketMqListenerContainerCustomizer(properties);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);

        when(container.getConsumerGroup()).thenReturn("call-third-party-dlq-group");
        when(container.getConsumer()).thenReturn(consumer);

        customizer.postProcessAfterInitialization(container, "rocketMqThirdPartyDeadLetterConsumer");

        verify(consumer).setConsumeThreadMax(2);
        verify(consumer).setMaxReconsumeTimes(10);
    }
}
