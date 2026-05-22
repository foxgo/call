package com.callcenter.ingestion.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldBindNameserverTopicsAndConsumerConcurrency() {
        contextRunner
                .withPropertyValues(
                        "call.rocketmq.name-server=127.0.0.1:9876",
                        "call.rocketmq.producer-group=call-producer-group",
                        "call.rocketmq.topics.record-ingest=call_record_ingest",
                        "call.rocketmq.topics.round-ingest=call_round_ingest",
                        "call.rocketmq.topics.record-dlq=call_record_dlq",
                        "call.rocketmq.topics.round-dlq=call_round_dlq",
                        "call.rocketmq.consumers.record.group=call-record-consumer-group",
                        "call.rocketmq.consumers.record.consume-thread-max=8",
                        "call.rocketmq.consumers.record.max-reconsume-times=4",
                        "call.rocketmq.consumers.round.group=call-round-consumer-group",
                        "call.rocketmq.consumers.round.consume-thread-max=6",
                        "call.rocketmq.consumers.round.max-reconsume-times=5",
                        "call.rocketmq.consumers.index.group=call-index-group",
                        "call.rocketmq.consumers.index.consume-thread-max=4",
                        "call.rocketmq.consumers.index.max-reconsume-times=6",
                        "call.rocketmq.consumers.ai.group=call-ai-group",
                        "call.rocketmq.consumers.ai.consume-thread-max=2",
                        "call.rocketmq.consumers.ai.max-reconsume-times=7",
                        "call.rocketmq.consumers.third-party.group=call-third-party-group",
                        "call.rocketmq.consumers.third-party.consume-thread-max=3",
                        "call.rocketmq.consumers.third-party.max-reconsume-times=8"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RocketMqProperties properties = context.getBean(RocketMqProperties.class);
                    assertThat(properties.getNameServer()).isEqualTo("127.0.0.1:9876");
                    assertThat(properties.getProducerGroup()).isEqualTo("call-producer-group");
                    assertThat(properties.getTopics().getRecordIngest()).isEqualTo("call_record_ingest");
                    assertThat(properties.getTopics().getRoundIngest()).isEqualTo("call_round_ingest");
                    assertThat(properties.getTopics().getRecordDlq()).isEqualTo("call_record_dlq");
                    assertThat(properties.getTopics().getRoundDlq()).isEqualTo("call_round_dlq");
                    assertThat(properties.getConsumers().getRecord().getGroup()).isEqualTo("call-record-consumer-group");
                    assertThat(properties.getConsumers().getRecord().getConsumeThreadMax()).isEqualTo(8);
                    assertThat(properties.getConsumers().getRecord().getMaxReconsumeTimes()).isEqualTo(4);
                    assertThat(properties.getConsumers().getRound().getGroup()).isEqualTo("call-round-consumer-group");
                    assertThat(properties.getConsumers().getRound().getConsumeThreadMax()).isEqualTo(6);
                    assertThat(properties.getConsumers().getRound().getMaxReconsumeTimes()).isEqualTo(5);
                    assertThat(properties.getConsumers().getIndex().getConsumeThreadMax()).isEqualTo(4);
                    assertThat(properties.getConsumers().getIndex().getMaxReconsumeTimes()).isEqualTo(6);
                    assertThat(properties.getConsumers().getAi().getConsumeThreadMax()).isEqualTo(2);
                    assertThat(properties.getConsumers().getAi().getMaxReconsumeTimes()).isEqualTo(7);
                    assertThat(properties.getConsumers().getThirdParty().getGroup()).isEqualTo("call-third-party-group");
                    assertThat(properties.getConsumers().getThirdParty().getConsumeThreadMax()).isEqualTo(3);
                    assertThat(properties.getConsumers().getThirdParty().getMaxReconsumeTimes()).isEqualTo(8);
                });
    }

    @Test
    void shouldRejectNonPositiveConsumerConcurrency() {
        contextRunner
                .withPropertyValues(
                        "call.rocketmq.name-server=127.0.0.1:9876",
                        "call.rocketmq.consumers.record.group=call-record-consumer-group",
                        "call.rocketmq.consumers.record.consume-thread-max=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .hasStackTraceContaining("consumeThreadMax");
                });
    }

    @Configuration
    @EnableConfigurationProperties(RocketMqProperties.class)
    static class TestConfig {
    }
}
