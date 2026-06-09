package com.callcenter.ingestion.service;

import java.time.Duration;

public interface MessagePublisher {

    void publish(String topic, String key, String payload);

    void publishDelayed(String topic, String key, String payload, Duration delay);
}
