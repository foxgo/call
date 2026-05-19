package com.callcenter.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ShardProperties.class,
        CallIdProperties.class,
        CallElasticsearchProperties.class,
        CallDatasourceProperties.class
})
public class CallCommonAutoConfiguration {
}

