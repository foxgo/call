package com.callcenter.ingestion.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class RocketMqListenerContainerCustomizer implements BeanPostProcessor {

    private final Map<String, ConsumerSettings> settingsByGroup;

    public RocketMqListenerContainerCustomizer(RocketMqProperties properties) {
        this.settingsByGroup = new HashMap<>();
        put(properties.getConsumers().getRecord());
        put(properties.getConsumers().getRound());
        put(properties.getConsumers().getIndex());
        put(properties.getConsumers().getRecordDlq());
        put(properties.getConsumers().getRoundDlq());
        put(properties.getConsumers().getIndexDlq());
        put(properties.getConsumers().getAi());
        put(properties.getConsumers().getAiDlq());
        put(properties.getConsumers().getThirdParty());
        put(properties.getConsumers().getThirdPartyDlq());
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DefaultRocketMQListenerContainer container)) {
            return bean;
        }

        ConsumerSettings settings = settingsByGroup.get(container.getConsumerGroup());
        if (settings == null) {
            return bean;
        }

        DefaultMQPushConsumer consumer = container.getConsumer();
        if (consumer != null) {
            consumer.setConsumeThreadMax(settings.consumeThreadMax());
            consumer.setMaxReconsumeTimes(settings.maxReconsumeTimes());
        }
        return bean;
    }

    private void put(RocketMqProperties.Consumer consumer) {
        settingsByGroup.put(
                consumer.getGroup(),
                new ConsumerSettings(consumer.getConsumeThreadMax(), consumer.getMaxReconsumeTimes())
        );
    }

    private record ConsumerSettings(int consumeThreadMax, int maxReconsumeTimes) {
    }
}
