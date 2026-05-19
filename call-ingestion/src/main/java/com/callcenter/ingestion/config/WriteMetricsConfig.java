package com.callcenter.ingestion.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WriteMetricsConfig {

    @Bean
    public WriteMetrics writeMetrics(MeterRegistry registry) {
        return new WriteMetrics(registry);
    }
}
