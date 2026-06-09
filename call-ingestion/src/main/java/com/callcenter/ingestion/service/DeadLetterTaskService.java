package com.callcenter.ingestion.service;

import com.callcenter.ingestion.service.DeadLetterTaskRepository;
import com.callcenter.ingestion.model.DomainEventMessage;
import com.callcenter.ingestion.model.DeadLetterTaskData;
import com.callcenter.ingestion.model.CallRecordMessage;
import com.callcenter.ingestion.model.CallRoundMessage;
import com.callcenter.ingestion.model.MessageKeys;
import com.callcenter.ingestion.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Service;

@Service
public class DeadLetterTaskService {

    private static final String NEW_STATUS = "NEW";
    private static final String RECORD_INGEST_PAYLOAD_TYPE = "RECORD_INGEST";
    private static final String ROUND_INGEST_PAYLOAD_TYPE = "ROUND_INGEST";

    private final DeadLetterTaskRepository repository;
    private final ObjectMapper objectMapper;

    public DeadLetterTaskService(DeadLetterTaskRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void persist(MessageExt messageExt, MessageType messageType) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String rawPayload = new String(messageExt.getBody(), StandardCharsets.UTF_8);
        PayloadMetadata payloadMetadata = readPayloadMetadata(rawPayload, messageType);
        DeadLetterTaskData task = new DeadLetterTaskData(
                taskKey(messageExt),
                messageType.name(),
                sourceTopic(messageExt),
                sourcePartition(messageExt),
                messageExt.getQueueOffset(),
                messageExt.getTopic(),
                messageExt.getQueueOffset(),
                messageExt.getProperty(MessageConst.PROPERTY_ORIGIN_MESSAGE_ID),
                payloadMetadata.messageKey(),
                payloadMetadata.idempotencyKey(),
                payloadMetadata.payloadType(),
                rawPayload,
                NEW_STATUS,
                messageExt.getReconsumeTimes(),
                intProperty(messageExt, MessageConst.PROPERTY_MAX_RECONSUME_TIMES),
                null,
                toUtcTime(messageExt.getStoreTimestamp()),
                null,
                null,
                now,
                now
        );
        repository.insertIgnore(task);
    }

    private String taskKey(MessageExt messageExt) {
        String originMessageId = messageExt.getProperty(MessageConst.PROPERTY_ORIGIN_MESSAGE_ID);
        if (originMessageId != null && !originMessageId.isBlank()) {
            return messageExt.getTopic() + ":" + originMessageId;
        }
        return messageExt.getTopic() + ":" + messageExt.getMsgId();
    }

    private String sourceTopic(MessageExt messageExt) {
        String retryTopic = messageExt.getProperty(MessageConst.PROPERTY_RETRY_TOPIC);
        if (retryTopic != null && !retryTopic.isBlank()) {
            return retryTopic;
        }
        String realTopic = messageExt.getProperty(MessageConst.PROPERTY_REAL_TOPIC);
        if (realTopic != null && !realTopic.isBlank()) {
            return realTopic;
        }
        return messageExt.getTopic();
    }

    private int sourcePartition(MessageExt messageExt) {
        String realQueueId = messageExt.getProperty(MessageConst.PROPERTY_REAL_QUEUE_ID);
        if (realQueueId != null && !realQueueId.isBlank()) {
            return Integer.parseInt(realQueueId);
        }
        return messageExt.getQueueId();
    }

    private PayloadMetadata readPayloadMetadata(String rawPayload, MessageType messageType) {
        try {
            return switch (messageType) {
                case RECORD -> {
                    CallRecordMessage message = objectMapper.readValue(rawPayload, CallRecordMessage.class);
                    String idempotencyKey = MessageKeys.recordIdempotencyKey(message);
                    yield new PayloadMetadata(idempotencyKey, idempotencyKey, RECORD_INGEST_PAYLOAD_TYPE);
                }
                case ROUND -> {
                    CallRoundMessage message = objectMapper.readValue(rawPayload, CallRoundMessage.class);
                    String idempotencyKey = MessageKeys.roundIdempotencyKey(message);
                    yield new PayloadMetadata(idempotencyKey, idempotencyKey, ROUND_INGEST_PAYLOAD_TYPE);
                }
                case INDEX, AI, THIRD_PARTY -> {
                    DomainEventMessage event = objectMapper.readValue(rawPayload, DomainEventMessage.class);
                    String idempotencyKey = MessageKeys.domainEventIdempotencyKey(event.eventId());
                    yield new PayloadMetadata(event.eventId(), idempotencyKey, event.eventType());
                }
            };
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析自动死信原始消息", exception);
        }
    }

    private int intProperty(MessageExt messageExt, String propertyName) {
        String value = messageExt.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private LocalDateTime toUtcTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private record PayloadMetadata(
            String messageKey,
            String idempotencyKey,
            String payloadType
    ) {
    }
}
