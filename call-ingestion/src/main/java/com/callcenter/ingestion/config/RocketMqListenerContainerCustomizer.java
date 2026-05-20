package com.callcenter.ingestion.config;

import java.util.Map;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class RocketMqListenerContainerCustomizer implements BeanPostProcessor {

    private final Map<String, Integer> consumeThreadMaxByGroup;

    public RocketMqListenerContainerCustomizer(RocketMqProperties properties) {
        this.consumeThreadMaxByGroup = Map.of(
                properties.getConsumers().getRecord().getGroup(),
                properties.getConsumers().getRecord().getConsumeThreadMax(),
                properties.getConsumers().getRound().getGroup(),
                properties.getConsumers().getRound().getConsumeThreadMax(),
                properties.getConsumers().getIndex().getGroup(),
                properties.getConsumers().getIndex().getConsumeThreadMax(),
                properties.getConsumers().getAi().getGroup(),
                properties.getConsumers().getAi().getConsumeThreadMax(),
                properties.getConsumers().getThirdParty().getGroup(),
                properties.getConsumers().getThirdParty().getConsumeThreadMax()
        );
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DefaultRocketMQListenerContainer container)) {
            return bean;
        }

        Integer consumeThreadMax = consumeThreadMaxByGroup.get(container.getConsumerGroup());
        if (consumeThreadMax == null) {
            return bean;
        }

        DefaultMQPushConsumer consumer = container.getConsumer();
        if (consumer != null) {
            consumer.setConsumeThreadMax(consumeThreadMax);
        }
        return bean;
    }
}
