package com.callcenter.ingestion.infrastructure.config;

import com.callcenter.ingestion.infrastructure.mq.MessagePublisher;
import com.callcenter.ingestion.application.outbox.OutboxPublisher;
import com.callcenter.ingestion.infrastructure.outbox.OutboxPublisherProperties;
import com.callcenter.ingestion.infrastructure.outbox.persistence.OutboxRepository;
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
                        "call.postprocess.topics.analysis-completed=analysis.completed.v1",
                        "call.postprocess.llm-enabled=true",
                        "call.outbox.batch-size=50",
                        "call.outbox.poll-interval=PT3S",
                        "call.outbox.processing-timeout=PT2M",
                        "call.outbox.max-retries=7"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PostprocessProperties properties = context.getBean(PostprocessProperties.class);
                    OutboxPublisherProperties outboxProperties = context.getBean(OutboxPublisherProperties.class);
                    assertThat(properties.getTopics().getRecordPersisted()).isEqualTo("record.persisted.v1");
                    assertThat(properties.getTopics().getAnalysisCompleted()).isEqualTo("analysis.completed.v1");
                    assertThat(properties.isLlmEnabled()).isTrue();
                    assertThat(outboxProperties.getProcessingTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
                    assertThat(outboxProperties.getMaxRetries()).isEqualTo(7);
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
            assertThat(context.getBean(PostprocessProperties.class).getTopics().getAnalysisCompleted())
                    .isEqualTo("call_record_analysis_completed");
            assertThat(context.getBean(PostprocessProperties.class).isLlmEnabled()).isFalse();
            assertThat(context.getBean(OutboxPublisherProperties.class).getProcessingTimeout())
                    .isEqualTo(java.time.Duration.ofMinutes(5));
            assertThat(context.getBean(OutboxPublisherProperties.class).getMaxRetries()).isEqualTo(10);
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
        MessagePublisher messagePublisher() {
            return mock(MessagePublisher.class);
        }

        @Bean
        OutboxPublisher outboxPublisher(
                OutboxRepository repository,
                MessagePublisher messagePublisher,
                OutboxPublisherProperties outboxPublisherProperties,
                PostprocessProperties postprocessProperties
        ) {
            return new OutboxPublisher(repository, messagePublisher, outboxPublisherProperties, postprocessProperties);
        }
    }
}
