package com.callcenter.ingestion.infrastructure.outbox;

import com.callcenter.ingestion.application.port.OutboxPublisherSettings;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "call.outbox")
public class OutboxPublisherProperties implements OutboxPublisherSettings {

    @Min(1)
    private int batchSize = 100;

    @Min(1)
    private int maxRetries = 10;

    private Duration pollInterval = Duration.ofSeconds(5);

    private Duration retryBackoff = Duration.ofSeconds(30);

    private Duration processingTimeout = Duration.ofMinutes(5);

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public int maxRetries() {
        return maxRetries;
    }

    @Override
    public Duration retryBackoff() {
        return retryBackoff;
    }

    @Override
    public Duration processingTimeout() {
        return processingTimeout;
    }
}
