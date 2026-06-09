package com.callcenter.ingestion.mq;

import com.callcenter.ingestion.config.RocketMqCompatibilityConfig;
import com.callcenter.ingestion.service.MessagePublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqInfrastructureBeanWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "rocketmq.name-server=127.0.0.1:9876",
                    "rocketmq.producer.group=call-producer-group"
            )
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldCreateRocketMqTemplateAndPublisherBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RocketMQTemplate.class);
            assertThat(context).hasSingleBean(RocketMqMessagePublisher.class);
            assertThat(context).hasSingleBean(MessagePublisher.class);
        });
    }

    @Configuration
    @Import({RocketMqCompatibilityConfig.class, RocketMqMessagePublisher.class})
    static class TestConfig {
    }
}
