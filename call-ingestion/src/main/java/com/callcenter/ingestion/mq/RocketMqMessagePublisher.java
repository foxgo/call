package com.callcenter.ingestion.mq;

import com.callcenter.ingestion.service.MessagePublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
public class RocketMqMessagePublisher implements MessagePublisher {

    private static final List<Duration> SUPPORTED_DELAYS = List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(3),
            Duration.ofMinutes(4),
            Duration.ofMinutes(5),
            Duration.ofMinutes(6),
            Duration.ofMinutes(7),
            Duration.ofMinutes(8),
            Duration.ofMinutes(9),
            Duration.ofMinutes(10),
            Duration.ofMinutes(20),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(2)
    );

    private final RocketMQTemplate rocketMQTemplate;

    public RocketMqMessagePublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void publish(String topic, String key, String payload) {
        rocketMQTemplate.syncSend(topic, payload);
    }

    @Override
    public void publishDelayed(String topic, String key, String payload, Duration delay) {
        Message message = new Message(topic, payload.getBytes(StandardCharsets.UTF_8));
        message.setKeys(key);
        message.setDelayTimeLevel(resolveDelayLevel(delay));
        try {
            rocketMQTemplate.getProducer().send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("发送 RocketMQ 延迟消息失败", exception);
        }
    }

    private int resolveDelayLevel(Duration delay) {
        int index = SUPPORTED_DELAYS.indexOf(delay);
        if (index < 0) {
            throw new IllegalArgumentException("不支持的 RocketMQ 延迟级别: " + delay);
        }
        return index + 1;
    }
}
