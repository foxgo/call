package com.callcenter.persistence.util;

import com.callcenter.persistence.config.CallIdProperties;
import com.callcenter.persistence.config.ShardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ShardedSnowflakeIdGeneratorConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GeneratorScanConfig.class, PropertiesConfig.class);

    @Test
    void shouldRegisterGeneratorBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ShardedSnowflakeIdGenerator.class));
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = ShardedSnowflakeIdGenerator.class)
    static class GeneratorScanConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({CallIdProperties.class, ShardProperties.class})
    static class PropertiesConfig {
    }
}
