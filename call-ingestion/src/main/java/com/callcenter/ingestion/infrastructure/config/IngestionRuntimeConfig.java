package com.callcenter.ingestion.infrastructure.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        PostprocessProperties.class,
        RocketMqProperties.class,
        com.callcenter.ingestion.infrastructure.outbox.OutboxPublisherProperties.class
})
public class IngestionRuntimeConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
