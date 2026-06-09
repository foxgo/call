package com.callcenter.ingestion.repository;

import com.callcenter.ingestion.service.DeadLetterTaskRepository;
import com.callcenter.ingestion.model.DeadLetterTaskData;
import org.springframework.stereotype.Repository;

@Repository
public class DeadLetterTaskRepositoryImpl implements DeadLetterTaskRepository {

    private final CallDeadLetterTaskMapper mapper;

    public DeadLetterTaskRepositoryImpl(CallDeadLetterTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insertIgnore(DeadLetterTaskData task) {
        return mapper.insertIgnore(toEntity(task));
    }

    private CallDeadLetterTaskEntity toEntity(DeadLetterTaskData task) {
        CallDeadLetterTaskEntity entity = new CallDeadLetterTaskEntity();
        entity.setTaskKey(task.taskKey());
        entity.setMessageType(task.messageType());
        entity.setSourceTopic(task.sourceTopic());
        entity.setSourcePartition(task.sourcePartition());
        entity.setSourceOffset(task.sourceOffset());
        entity.setDlqTopic(task.dlqTopic());
        entity.setDlqQueueOffset(task.dlqQueueOffset());
        entity.setOriginMessageId(task.originMessageId());
        entity.setMessageKey(task.messageKey());
        entity.setIdempotencyKey(task.idempotencyKey());
        entity.setPayloadType(task.payloadType());
        entity.setPayload(task.payload());
        entity.setStatus(task.status());
        entity.setDlqAttempt(task.dlqAttempt());
        entity.setDlqMaxAttempts(task.dlqMaxAttempts());
        entity.setFirstFailureAt(task.firstFailureAt());
        entity.setLastFailureAt(task.lastFailureAt());
        entity.setErrorClass(task.errorClass());
        entity.setErrorMessage(task.errorMessage());
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
        return entity;
    }
}
