package com.callcenter.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("call_record")
public class CallRecordEntity {

    @TableId
    private Long callId;
    private Long tenantId;
    private Long taskId;
    private String phone;
    private String lineNumber;
    private Integer callStatus;
    private Integer duration;
    private Integer roundTotal;
    private String recordingUrl;
    private Integer errorCode;
    private String errorDescription;
    private Byte hangupBy;
    private Byte connected;
    private Long ringDuration;
    private LocalDateTime ringStartTime;
    private LocalDateTime hangupTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;

    public Long getCallId() {
        return callId;
    }

    public void setCallId(Long callId) {
        this.callId = callId;
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

    public Integer getRoundTotal() {
        return roundTotal;
    }

    public void setRoundTotal(Integer roundTotal) {
        this.roundTotal = roundTotal;
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

    public LocalDateTime getRingStartTime() {
        return ringStartTime;
    }

    public void setRingStartTime(LocalDateTime ringStartTime) {
        this.ringStartTime = ringStartTime;
    }

    public LocalDateTime getHangupTime() {
        return hangupTime;
    }

    public void setHangupTime(LocalDateTime hangupTime) {
        this.hangupTime = hangupTime;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
