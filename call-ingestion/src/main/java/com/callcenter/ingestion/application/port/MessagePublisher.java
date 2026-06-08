package com.callcenter.ingestion.application.port;

import java.time.Duration;

public interface MessagePublisher {

    void publish(String topic, String key, String payload);

    void publishDelayed(String topic, String key, String payload, Duration delay);
}
