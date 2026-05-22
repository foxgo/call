package com.callcenter.ingestion.consumer;

import com.callcenter.ingestion.config.RocketMqProperties;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseRocketMQListener implements RocketMQPushConsumerLifecycleListener, DisposableBean {

    private DefaultMQPushConsumer consumer;

    @Autowired
    protected RocketMqProperties config;

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.suspend(); // 停止拉取新消息
            consumer.shutdown();// 优雅关闭
        }
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        this.consumer = consumer;
        this.consumer.setMaxReconsumeTimes(3);
    }

}
