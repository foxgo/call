package com.callcenter.ingestion.config;

import com.callcenter.ingestion.mq.OrderedMessagePublisher;
import com.callcenter.ingestion.outbox.OutboxPublisher;
import com.callcenter.ingestion.outbox.OutboxPublisherProperties;
import com.callcenter.ingestion.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PostprocessPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldBindPersistedTopicsFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "call.postprocess.topics.record-persisted=record.persisted.v1",
                        "call.postprocess.topics.round-persisted=round.persisted.v1",
                        "call.outbox.batch-size=50",
                        "call.outbox.poll-interval=PT3S"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PostprocessProperties properties = context.getBean(PostprocessProperties.class);
                    assertThat(properties.getTopics().getRecordPersisted()).isEqualTo("record.persisted.v1");
                    assertThat(properties.getTopics().getRoundPersisted()).isEqualTo("round.persisted.v1");
                });
    }

    @Test
    void shouldRejectBlankPersistedTopic() {
        contextRunner
                .withPropertyValues("call.postprocess.topics.record-persisted=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("recordPersisted");
                });
    }

    @Test
    void shouldCreatePublisherBeanWithDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PostprocessProperties.class);
            assertThat(context).hasSingleBean(OutboxPublisherProperties.class);
            assertThat(context).hasSingleBean(OutboxPublisher.class);
            assertThat(context.getBean(PostprocessProperties.class).getTopics().getRecordPersisted())
                    .isEqualTo("call_record_persisted");
        });
    }

    @Configuration
    @EnableConfigurationProperties({PostprocessProperties.class, OutboxPublisherProperties.class})
    static class TestConfig {

        @Bean
        OutboxRepository outboxRepository() {
            return mock(OutboxRepository.class);
        }

        @Bean
        OrderedMessagePublisher orderedMessagePublisher() {
            return mock(OrderedMessagePublisher.class);
        }

        @Bean
        OutboxPublisher outboxPublisher(
                OutboxRepository repository,
                OrderedMessagePublisher orderedMessagePublisher,
                OutboxPublisherProperties outboxPublisherProperties,
                PostprocessProperties postprocessProperties
        ) {
            return new OutboxPublisher(repository, orderedMessagePublisher, outboxPublisherProperties, postprocessProperties);
        }
    }
}
