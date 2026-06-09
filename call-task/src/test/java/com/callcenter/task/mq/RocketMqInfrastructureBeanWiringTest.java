package com.callcenter.task.mq;

import com.callcenter.task.config.CallTaskRocketMqProperties;
import com.callcenter.task.config.RocketMqCompatibilityConfig;
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
                    "rocketmq.producer.group=call-task-producer-group",
                    "call.task.rocketmq.topics.dispatch=call_dispatch_topic"
            )
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldCreateRocketMqTemplateAndDialDispatchPublisherBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RocketMQTemplate.class);
            assertThat(context).hasSingleBean(DialDispatchPublisher.class);
        });
    }

    @Configuration
    @Import({RocketMqCompatibilityConfig.class, CallTaskRocketMqProperties.class, DialDispatchPublisher.class})
    static class TestConfig {
    }
}
