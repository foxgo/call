package com.callcenter.task.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class CreateTaskRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @Min(1)
    private int maxConcurrency;

    @Min(1)
    @Max(4)
    private int priority = 4;

    @Size(max = 32)
    private String callerIdMode = "HYBRID";

    @Size(max = 32)
    private String optimizationGoal = "ANSWER";

    private Double answerWeight = 1D;

    private Double conversionWeight = 0D;

    private Double costWeight = 0D;

    private Double riskWeight = 0D;

    private Boolean localPresenceEnabled = false;

    @Min(0)
    private Integer sameCallerCooldownSeconds = 3600;

    @Min(1)
    private Integer maxCallerExposurePerHour = 200;

    private LocalDateTime startTime;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
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
}
