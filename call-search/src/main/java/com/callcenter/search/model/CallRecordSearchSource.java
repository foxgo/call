package com.callcenter.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CallRecordSearchSource {

    @JsonProperty("tenant_id")
    private Long tenantId;

    @JsonProperty("call_id")
    private String callId;

    @JsonProperty("task_id")
    private Long taskId;

    private String phone;

    @JsonProperty("line_number")
    private String lineNumber;

    @JsonProperty("call_status")
    private Integer callStatus;

    private Integer duration;

    @JsonProperty("recording_url")
    private String recordingUrl;

    @JsonProperty("error_code")
    private Integer errorCode;

    @JsonProperty("error_description")
    private String errorDescription;

    @JsonProperty("hangup_by")
    private Byte hangupBy;

    private Byte connected;

    @JsonProperty("ring_duration")
    private Long ringDuration;

    @JsonProperty("ring_start_time")
    private String ringStartTime;

    @JsonProperty("hangup_time")
    private String hangupTime;

    @JsonProperty("start_time")
    private String startTime;

    @JsonProperty("end_time")
    private String endTime;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("full_text")
    private String fullText;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Integer getCallStatus() {
        return callStatus;
    }

    public void setCallStatus(Integer callStatus) {
        this.callStatus = callStatus;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getRecordingUrl() {
        return recordingUrl;
    }

    public void setRecordingUrl(String recordingUrl) {
        this.recordingUrl = recordingUrl;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public Byte getHangupBy() {
        return hangupBy;
    }

    public void setHangupBy(Byte hangupBy) {
        this.hangupBy = hangupBy;
    }

    public Byte getConnected() {
        return connected;
    }

    public void setConnected(Byte connected) {
        this.connected = connected;
    }

    public Long getRingDuration() {
        return ringDuration;
    }

    public void setRingDuration(Long ringDuration) {
        this.ringDuration = ringDuration;
    }

    public String getRingStartTime() {
        return ringStartTime;
    }

    public void setRingStartTime(String ringStartTime) {
        this.ringStartTime = ringStartTime;
    }

    public String getHangupTime() {
        return hangupTime;
    }

    public void setHangupTime(String hangupTime) {
        this.hangupTime = hangupTime;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }
}
