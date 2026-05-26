package com.callcenter.task.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DialResultCallbackRequest {

    @NotNull
    private Long taskId;

    @NotNull
    private Long dialUnitId;

    @NotBlank
    private String dispatchToken;

    @NotBlank
    private String resultStatus;

    private String failureCode;

    private String failureReason;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getDialUnitId() {
        return dialUnitId;
    }

    public void setDialUnitId(Long dialUnitId) {
        this.dialUnitId = dialUnitId;
    }

    public String getDispatchToken() {
        return dispatchToken;
    }

    public void setDispatchToken(String dispatchToken) {
        this.dispatchToken = dispatchToken;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
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

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(resultStatus);
    }
}
