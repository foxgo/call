package com.callcenter.ingestion.config;

import com.callcenter.ingestion.service.CallAnalysisGateway;
import com.callcenter.ingestion.service.ThirdPartyPushGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostprocessClientFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(CallAnalysisGateway.class)
    CallAnalysisGateway callAnalysisGateway() {
        return request -> {
            throw missingClient("CallAnalysisGateway", request);
        };
    }

    @Bean
    @ConditionalOnMissingBean(ThirdPartyPushGateway.class)
    ThirdPartyPushGateway thirdPartyPushGateway() {
        return request -> {
            throw missingClient("ThirdPartyPushGateway", request);
        };
    }

    private static IllegalStateException missingClient(String clientType, Object request) {
        String requestType = request == null ? "unknown" : request.getClass().getSimpleName();
        return new IllegalStateException(clientType + " not configured for request type " + requestType);
    }
}
