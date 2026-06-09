package com.callcenter.ingestion.postprocess;

import com.callcenter.ingestion.config.OutboxPublisherProperties;
import com.callcenter.ingestion.config.PostprocessProperties;
import com.callcenter.ingestion.service.MessagePublisher;
import com.callcenter.ingestion.service.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxPublisherBeanWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldCreateComponentBeanUsingConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxPublisher.class);
        });
    }

    @Configuration
    @EnableConfigurationProperties({PostprocessProperties.class, OutboxPublisherProperties.class})
    @Import(OutboxPublisher.class)
    static class TestConfig {

        @Bean
        OutboxEventRepository outboxEventRepository() {
            return mock(OutboxEventRepository.class);
        }

        @Bean
        MessagePublisher messagePublisher() {
            return mock(MessagePublisher.class);
        }
    }
}
