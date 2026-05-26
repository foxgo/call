package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallDeadLetterTaskEntity;
import com.callcenter.common.mapper.CallDeadLetterTaskMapper;
import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.processor.MessageKeys;
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

    private final CallDeadLetterTaskMapper mapper;
    private final ObjectMapper objectMapper;

    public DeadLetterTaskService(CallDeadLetterTaskMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void persist(MessageExt messageExt, MessageType messageType) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String rawPayload = new String(messageExt.getBody(), StandardCharsets.UTF_8);
        PayloadMetadata payloadMetadata = readPayloadMetadata(rawPayload, messageType);
        CallDeadLetterTaskEntity task = new CallDeadLetterTaskEntity();
        task.setTaskKey(taskKey(messageExt));
        task.setMessageType(messageType.name());
        task.setSourceTopic(sourceTopic(messageExt));
        task.setSourcePartition(sourcePartition(messageExt));
        task.setSourceOffset(messageExt.getQueueOffset());
        task.setDlqTopic(messageExt.getTopic());
        task.setDlqQueueOffset(messageExt.getQueueOffset());
        task.setOriginMessageId(messageExt.getProperty(MessageConst.PROPERTY_ORIGIN_MESSAGE_ID));
        task.setMessageKey(payloadMetadata.messageKey());
        task.setIdempotencyKey(payloadMetadata.idempotencyKey());
        task.setPayloadType(payloadMetadata.payloadType());
        task.setPayload(rawPayload);
        task.setStatus(NEW_STATUS);
        task.setDlqAttempt(messageExt.getReconsumeTimes());
        task.setDlqMaxAttempts(intProperty(messageExt, MessageConst.PROPERTY_MAX_RECONSUME_TIMES));
        task.setFirstFailureAt(null);
        task.setLastFailureAt(toUtcTime(messageExt.getStoreTimestamp()));
        task.setErrorClass(null);
        task.setErrorMessage(null);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        mapper.insertIgnore(task);
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
