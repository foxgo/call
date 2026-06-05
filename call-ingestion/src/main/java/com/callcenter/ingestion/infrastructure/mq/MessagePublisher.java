package com.callcenter.ingestion.infrastructure.mq;

import java.time.Duration;

public interface MessagePublisher {

    void publish(String topic, String key, String payload);

    void publishDelayed(String topic, String key, String payload, Duration delay);
}
