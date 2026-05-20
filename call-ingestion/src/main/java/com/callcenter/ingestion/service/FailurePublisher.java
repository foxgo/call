package com.callcenter.ingestion.service;

import com.callcenter.common.dto.RetryMessageEnvelope;
import com.callcenter.ingestion.config.RocketMqProperties;
import com.callcenter.ingestion.config.WriteMetrics;
import com.callcenter.ingestion.mq.OrderedMessagePublisher;
import com.callcenter.ingestion.model.InboundMessage;
import com.callcenter.ingestion.model.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;

@Service
public class FailurePublisher {

    private final OrderedMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;
    private final WriteMetrics writeMetrics;
    private final RocketMqProperties rocketMqProperties;

    public FailurePublisher(
            OrderedMessagePublisher messagePublisher,
            ObjectMapper objectMapper,
            WriteMetrics writeMetrics,
            RocketMqProperties rocketMqProperties
    ) {
        this.messagePublisher = messagePublisher;
        this.objectMapper = objectMapper;
        this.writeMetrics = writeMetrics;
        this.rocketMqProperties = rocketMqProperties;
    }

    public <T> boolean publishDlq(InboundMessage<T> inbound, Exception exception) {
        return publish(
                dlqTopic(inbound.messageType()),
                inbound.messageKey(),
                buildEnvelope(
                        inbound.sourceTopic(),
                        inbound.sourcePartition(),
                        inbound.sourceOffset(),
                        inbound.messageType().name(),
                        inbound.messageKey(),
                        inbound.idempotencyKey(),
                        inbound.attempt(),
                        inbound.attempt(),
                        firstFailureAt(inbound),
                        exception,
                        serializePayload(inbound.payload())
                ),
                writeMetrics.dlqProduce()
        );
    }

    private RetryMessageEnvelope buildEnvelope(
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String payloadType,
            String messageKey,
            String idempotencyKey,
            int attempt,
            int maxAttempts,
            long firstFailureAt,
            Exception exception,
            String payload
    ) {
        long now = System.currentTimeMillis();
        return new RetryMessageEnvelope(
                payload,
                payloadType,
                messageKey,
                idempotencyKey,
                attempt,
                maxAttempts,
                sourceTopic,
                sourcePartition,
                sourceOffset,
                firstFailureAt == 0L ? now : firstFailureAt,
                now,
                exception.getClass().getName(),
                exception.getMessage()
        );
    }

    private boolean publish(String topic, String key, RetryMessageEnvelope envelope, Counter counter) {
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            messagePublisher.publish(topic, key, payload);
            increment(counter);
            return true;
        } catch (JsonProcessingException | RuntimeException exception) {
            return false;
        }
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payload", exception);
        }
    }

    private long firstFailureAt(InboundMessage<?> inbound) {
        return inbound.firstFailureAt() == 0L ? System.currentTimeMillis() : inbound.firstFailureAt();
    }

    private String dlqTopic(MessageType messageType) {
        return switch (messageType) {
            case RECORD -> rocketMqProperties.getTopics().getRecordDlq();
            case ROUND -> rocketMqProperties.getTopics().getRoundDlq();
        };
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
