package com.callcenter.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("call_caller_id_stats")
public class CallCallerIdStatsEntity {

    @TableId
    private Long id;
    private Long tenantId;
    private Long callerIdId;
    private String attemptStage;
    private LocalDateTime timeBucket;
    private Long attemptCount;
    private Long ringCount;
    private Long answerCount;
    private Long successCount;
    private Long totalTalkSeconds;
    private String failureCodeSummary;
    private Double healthScore;
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

    public Long getCallerIdId() {
        return callerIdId;
    }

    public void setCallerIdId(Long callerIdId) {
        this.callerIdId = callerIdId;
    }

    public String getAttemptStage() {
        return attemptStage;
    }

    public void setAttemptStage(String attemptStage) {
        this.attemptStage = attemptStage;
    }

    public LocalDateTime getTimeBucket() {
        return timeBucket;
    }

    public void setTimeBucket(LocalDateTime timeBucket) {
        this.timeBucket = timeBucket;
    }

    public Long getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Long attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Long getRingCount() {
        return ringCount;
    }

    public void setRingCount(Long ringCount) {
        this.ringCount = ringCount;
    }

    public Long getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(Long answerCount) {
        this.answerCount = answerCount;
    }

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getTotalTalkSeconds() {
        return totalTalkSeconds;
    }

    public void setTotalTalkSeconds(Long totalTalkSeconds) {
        this.totalTalkSeconds = totalTalkSeconds;
    }

    public String getFailureCodeSummary() {
        return failureCodeSummary;
    }

    public void setFailureCodeSummary(String failureCodeSummary) {
        this.failureCodeSummary = failureCodeSummary;
    }

    public Double getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(Double healthScore) {
        this.healthScore = healthScore;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
