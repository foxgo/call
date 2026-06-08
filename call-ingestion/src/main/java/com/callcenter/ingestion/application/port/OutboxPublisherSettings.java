package com.callcenter.ingestion.application.port;

import java.time.Duration;

public interface OutboxPublisherSettings {

    int batchSize();

    int maxRetries();

    Duration retryBackoff();

    Duration processingTimeout();
}
