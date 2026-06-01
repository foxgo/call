package com.callcenter.task.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "call.task.dispatch")
public class CallTaskDispatchProperties {

    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration retryScanInterval = Duration.ofSeconds(5);
    private Duration processingRecoveryInterval = Duration.ofSeconds(5);
    private Duration processingTimeout = Duration.ofMinutes(5);
    private Duration retryBackoff = Duration.ofSeconds(30);
    private Duration partitionLeaseTtl = Duration.ofSeconds(30);

    @Min(1)
    private int dispatchBatchSize = 100;

    @Min(1)
    private int preloadBatchSize = 100;

    @Min(1)
    private int preloadThreshold = 300;

    @Min(0)
    private int maxRetries = 3;

    @Min(1)
    private int shardCount = 16;

    @Min(1)
    private int partitionCount = 128;

    @Min(1)
    private int maxTasksPerPartitionTick = 8;

    @Min(1)
    private int dispatcherParallelism = 4;

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getRetryScanInterval() {
        return retryScanInterval;
    }

    public void setRetryScanInterval(Duration retryScanInterval) {
        this.retryScanInterval = retryScanInterval;
    }

    public Duration getProcessingRecoveryInterval() {
        return processingRecoveryInterval;
    }

    public void setProcessingRecoveryInterval(Duration processingRecoveryInterval) {
        this.processingRecoveryInterval = processingRecoveryInterval;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public Duration getPartitionLeaseTtl() {
        return partitionLeaseTtl;
    }

    public void setPartitionLeaseTtl(Duration partitionLeaseTtl) {
        this.partitionLeaseTtl = partitionLeaseTtl;
    }

    public int getDispatchBatchSize() {
        return dispatchBatchSize;
    }

    public void setDispatchBatchSize(int dispatchBatchSize) {
        this.dispatchBatchSize = dispatchBatchSize;
    }

    public int getPreloadBatchSize() {
        return preloadBatchSize;
    }

    public void setPreloadBatchSize(int preloadBatchSize) {
        this.preloadBatchSize = preloadBatchSize;
    }

    public int getPreloadThreshold() {
        return preloadThreshold;
    }

    public void setPreloadThreshold(int preloadThreshold) {
        this.preloadThreshold = preloadThreshold;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getShardCount() {
        return shardCount;
    }

    public void setShardCount(int shardCount) {
        this.shardCount = shardCount;
    }

    public int getPartitionCount() {
        return partitionCount;
    }

    public void setPartitionCount(int partitionCount) {
        this.partitionCount = partitionCount;
    }

    public int getMaxTasksPerPartitionTick() {
        return maxTasksPerPartitionTick;
    }

    public void setMaxTasksPerPartitionTick(int maxTasksPerPartitionTick) {
        this.maxTasksPerPartitionTick = maxTasksPerPartitionTick;
    }

    public int getDispatcherParallelism() {
        return dispatcherParallelism;
    }

    public void setDispatcherParallelism(int dispatcherParallelism) {
        this.dispatcherParallelism = dispatcherParallelism;
    }
}
