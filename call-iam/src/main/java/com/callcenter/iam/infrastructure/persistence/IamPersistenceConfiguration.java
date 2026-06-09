package com.callcenter.iam.infrastructure.persistence;

import com.callcenter.persistence.config.CallPersistenceConfiguration;
import com.callcenter.persistence.config.MybatisPlusConfig;
import com.callcenter.persistence.config.RoutingDataSourceConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        CallPersistenceConfiguration.class,
        MybatisPlusConfig.class,
        RoutingDataSourceConfig.class
})
@MapperScan("com.callcenter.iam.infrastructure.persistence.mapper")
@ConditionalOnProperty(name = "call.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class IamPersistenceConfiguration {
}
