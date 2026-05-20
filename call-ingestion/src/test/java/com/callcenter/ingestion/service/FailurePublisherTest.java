package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.RetryMessageEnvelope;
import com.callcenter.ingestion.config.RocketMqProperties;
import com.callcenter.ingestion.config.WriteMetrics;
import com.callcenter.ingestion.mq.OrderedMessagePublisher;
import com.callcenter.ingestion.model.InboundMessage;
import com.callcenter.ingestion.model.MessageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FailurePublisherTest {

    @Test
    void shouldPublishDlqEnvelopeWithSourceMetadataAndAttempt() throws Exception {
        OrderedMessagePublisher messagePublisher = mock(OrderedMessagePublisher.class);
        WriteMetrics writeMetrics = mock(WriteMetrics.class);
        RocketMqProperties properties = rocketMqProperties();

        FailurePublisher publisher = new FailurePublisher(messagePublisher, JsonSupport.objectMapper(), writeMetrics, properties);
        CallRecordMessage message = new CallRecordMessage(1001L, 7L, 55L, "13800138000", "021", 1, 1L, 2L, 3, 2, null);
        InboundMessage<CallRecordMessage> inbound = InboundMessage.main(
                "call_record_ingest",
                2,
                19L,
                "7",
                MessageType.RECORD,
                "1001",
                message
        );

        boolean published = publisher.publishDlq(inbound, new RuntimeException("mysql down"));

        assertThat(published).isTrue();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagePublisher).publish(
                eq("call_record_dlq"),
                eq("7"),
                payloadCaptor.capture()
        );

        RetryMessageEnvelope envelope = JsonSupport.objectMapper().readValue(payloadCaptor.getValue(), RetryMessageEnvelope.class);
        assertThat(envelope.sourceTopic()).isEqualTo("call_record_ingest");
        assertThat(envelope.sourcePartition()).isEqualTo(2);
        assertThat(envelope.sourceOffset()).isEqualTo(19L);
        assertThat(envelope.attempt()).isEqualTo(0);
        assertThat(envelope.idempotencyKey()).isEqualTo("1001");
        assertThat(envelope.payloadType()).isEqualTo("RECORD");
    }

    private RocketMqProperties rocketMqProperties() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getTopics().setRecordDlq("call_record_dlq");
        properties.getTopics().setRoundDlq("call_round_dlq");
        return properties;
    }
}
