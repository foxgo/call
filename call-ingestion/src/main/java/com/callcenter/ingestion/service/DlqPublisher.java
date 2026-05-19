package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.dto.FailedMessageEnvelope;
import com.callcenter.ingestion.config.WriteMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DlqPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final WriteMetrics writeMetrics;
    private final String recordDlqTopic;
    private final String roundDlqTopic;

    public DlqPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            WriteMetrics writeMetrics,
            @Value("${call.kafka.topics.record-dlq}") String recordDlqTopic,
            @Value("${call.kafka.topics.round-dlq}") String roundDlqTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.writeMetrics = writeMetrics;
        this.recordDlqTopic = recordDlqTopic;
        this.roundDlqTopic = roundDlqTopic;
    }

    public void publishRecord(CallRecordMessage message, Exception exception) {
        publish(recordDlqTopic, String.valueOf(message.tenantId()), message, exception);
    }

    public void publishRound(CallRoundMessage message, Exception exception) {
        publish(roundDlqTopic, String.valueOf(message.tenantId()), message, exception);
    }

    private void publish(String topic, String key, Object payload, Exception exception) {
        try {
            FailedMessageEnvelope envelope = new FailedMessageEnvelope(
                    topic,
                    key,
                    objectMapper.writeValueAsString(payload),
                    exception.getMessage(),
                    3
            );
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(envelope));
            writeMetrics.dlqProduce().increment();
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException("Failed to serialize DLQ payload", jsonProcessingException);
        }
    }
}
