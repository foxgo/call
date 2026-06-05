package com.callcenter.ingestion.infrastructure.deadletter.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("call_dead_letter_task")
public class CallDeadLetterTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskKey;
    private String messageType;
    private String sourceTopic;
    private Integer sourcePartition;
    private Long sourceOffset;
    private String dlqTopic;
    private Long dlqQueueOffset;
    private String originMessageId;
    private String messageKey;
    private String idempotencyKey;
    private String payloadType;
    private String payload;
    private String status;
    private Integer dlqAttempt;
    private Integer dlqMaxAttempts;
    private LocalDateTime firstFailureAt;
    private LocalDateTime lastFailureAt;
    private String errorClass;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getSourceTopic() {
        return sourceTopic;
    }

    public void setSourceTopic(String sourceTopic) {
        this.sourceTopic = sourceTopic;
    }

    public Integer getSourcePartition() {
        return sourcePartition;
    }

    public void setSourcePartition(Integer sourcePartition) {
        this.sourcePartition = sourcePartition;
    }

    public Long getSourceOffset() {
        return sourceOffset;
    }

    public void setSourceOffset(Long sourceOffset) {
        this.sourceOffset = sourceOffset;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public Long getDlqQueueOffset() {
        return dlqQueueOffset;
    }

    public void setDlqQueueOffset(Long dlqQueueOffset) {
        this.dlqQueueOffset = dlqQueueOffset;
    }

    public String getOriginMessageId() {
        return originMessageId;
    }

    public void setOriginMessageId(String originMessageId) {
        this.originMessageId = originMessageId;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDlqAttempt() {
        return dlqAttempt;
    }

    public void setDlqAttempt(Integer dlqAttempt) {
        this.dlqAttempt = dlqAttempt;
    }

    public Integer getDlqMaxAttempts() {
        return dlqMaxAttempts;
    }

    public void setDlqMaxAttempts(Integer dlqMaxAttempts) {
        this.dlqMaxAttempts = dlqMaxAttempts;
    }

    public LocalDateTime getFirstFailureAt() {
        return firstFailureAt;
    }

    public void setFirstFailureAt(LocalDateTime firstFailureAt) {
        this.firstFailureAt = firstFailureAt;
    }

    public LocalDateTime getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(LocalDateTime lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public String getErrorClass() {
        return errorClass;
    }

    public void setErrorClass(String errorClass) {
        this.errorClass = errorClass;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
