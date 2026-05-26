package com.callcenter.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("call_dial_unit")
public class CallDialUnitEntity {

    @TableId
    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long importBatchId;
    private String phone;
    private String status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private Float score;
    private LocalDateTime lastCallTime;
    private LocalDateTime nextCallTime;
    private String dispatchToken;
    private LocalDateTime inflightExpireAt;
    private String bizIdempotencyKey;
    private String failureCode;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(Long importBatchId) {
        this.importBatchId = importBatchId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public LocalDateTime getLastCallTime() {
        return lastCallTime;
    }

    public void setLastCallTime(LocalDateTime lastCallTime) {
        this.lastCallTime = lastCallTime;
    }

    public LocalDateTime getNextCallTime() {
        return nextCallTime;
    }

    public void setNextCallTime(LocalDateTime nextCallTime) {
        this.nextCallTime = nextCallTime;
    }

    public String getDispatchToken() {
        return dispatchToken;
    }

    public void setDispatchToken(String dispatchToken) {
        this.dispatchToken = dispatchToken;
    }

    public LocalDateTime getInflightExpireAt() {
        return inflightExpireAt;
    }

    public void setInflightExpireAt(LocalDateTime inflightExpireAt) {
        this.inflightExpireAt = inflightExpireAt;
    }

    public String getBizIdempotencyKey() {
        return bizIdempotencyKey;
    }

    public void setBizIdempotencyKey(String bizIdempotencyKey) {
        this.bizIdempotencyKey = bizIdempotencyKey;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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
