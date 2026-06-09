package com.callcenter.ingestion.service;

import java.time.Duration;

public interface OutboxPublisherSettings {

    int batchSize();

    int maxRetries();

    Duration retryBackoff();

    Duration processingTimeout();
}
