package com.callcenter.task.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("call_task")
public class CallTaskEntity {

    @TableId
    private Long id;
    private Long tenantId;
    private String name;
    private String status;
    private Integer totalCount;
    private Integer queuedCount;
    private Integer dialingCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer priority;
    private Integer maxConcurrency;
    private String callerIdMode;
    private String optimizationGoal;
    private Double answerWeight;
    private Double conversionWeight;
    private Double costWeight;
    private Double riskWeight;
    private Boolean localPresenceEnabled;
    private Integer sameCallerCooldownSeconds;
    private Integer maxCallerExposurePerHour;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer version;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getQueuedCount() {
        return queuedCount;
    }

    public void setQueuedCount(Integer queuedCount) {
        this.queuedCount = queuedCount;
    }

    public Integer getDialingCount() {
        return dialingCount;
    }

    public void setDialingCount(Integer dialingCount) {
        this.dialingCount = dialingCount;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public String getCallerIdMode() {
        return callerIdMode;
    }

    public void setCallerIdMode(String callerIdMode) {
        this.callerIdMode = callerIdMode;
    }

    public String getOptimizationGoal() {
        return optimizationGoal;
    }

    public void setOptimizationGoal(String optimizationGoal) {
        this.optimizationGoal = optimizationGoal;
    }

    public Double getAnswerWeight() {
        return answerWeight;
    }

    public void setAnswerWeight(Double answerWeight) {
        this.answerWeight = answerWeight;
    }

    public Double getConversionWeight() {
        return conversionWeight;
    }

    public void setConversionWeight(Double conversionWeight) {
        this.conversionWeight = conversionWeight;
    }

    public Double getCostWeight() {
        return costWeight;
    }

    public void setCostWeight(Double costWeight) {
        this.costWeight = costWeight;
    }

    public Double getRiskWeight() {
        return riskWeight;
    }

    public void setRiskWeight(Double riskWeight) {
        this.riskWeight = riskWeight;
    }

    public Boolean getLocalPresenceEnabled() {
        return localPresenceEnabled;
    }

    public void setLocalPresenceEnabled(Boolean localPresenceEnabled) {
        this.localPresenceEnabled = localPresenceEnabled;
    }

    public Integer getSameCallerCooldownSeconds() {
        return sameCallerCooldownSeconds;
    }

    public void setSameCallerCooldownSeconds(Integer sameCallerCooldownSeconds) {
        this.sameCallerCooldownSeconds = sameCallerCooldownSeconds;
    }

    public Integer getMaxCallerExposurePerHour() {
        return maxCallerExposurePerHour;
    }

    public void setMaxCallerExposurePerHour(Integer maxCallerExposurePerHour) {
        this.maxCallerExposurePerHour = maxCallerExposurePerHour;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
