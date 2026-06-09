package com.callcenter.persistence.config;



import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration

@EnableConfigurationProperties({
        ShardProperties.class,
        CallDatasourceProperties.class,
        CallIdProperties.class
})
public class CallPersistenceConfiguration {
}
