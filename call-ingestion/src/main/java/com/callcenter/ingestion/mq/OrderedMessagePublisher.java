package com.callcenter.ingestion.mq;

import java.time.Duration;

public interface OrderedMessagePublisher {

    void publish(String topic, String orderingKey, String payload);

    void publishDelayed(String topic, String orderingKey, String payload, Duration delay);
}
