package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.ingestion.processor.MessageBatchProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CallRoundConsumer {

    private final ObjectMapper objectMapper;
    private final MessageBatchProcessor batchProcessor;

    public CallRoundConsumer(ObjectMapper objectMapper, MessageBatchProcessor batchProcessor) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
    }

    @KafkaListener(
            topics = "${call.kafka.topics.round}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment)
            throws JsonProcessingException {
        List<CallRoundMessage> messages = records.stream()
                .map(record -> readValue(record.value(), CallRoundMessage.class))
                .toList();
        batchProcessor.processRoundBatch(messages);
        acknowledgment.acknowledge();
    }

    private <T> T readValue(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize message", exception);
        }
    }
}
