package com.callcenter.ingestion.config;

import com.callcenter.ingestion.support.metrics.WriteMetrics;
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
