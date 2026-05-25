package com.callcenter.ingestion.config;

import com.callcenter.ingestion.service.CallAnalysisClient;
import com.callcenter.ingestion.service.ThirdPartyPushClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostprocessClientFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(CallAnalysisClient.class)
    CallAnalysisClient callAnalysisClient() {
        return request -> {
            throw missingClient("CallAnalysisClient", request);
        };
    }

    @Bean
    @ConditionalOnMissingBean(ThirdPartyPushClient.class)
    ThirdPartyPushClient thirdPartyPushClient() {
        return request -> {
            throw missingClient("ThirdPartyPushClient", request);
        };
    }

    private static IllegalStateException missingClient(String clientType, Object request) {
        String requestType = request == null ? "unknown" : request.getClass().getSimpleName();
        return new IllegalStateException(clientType + " not configured for request type " + requestType);
    }
}
