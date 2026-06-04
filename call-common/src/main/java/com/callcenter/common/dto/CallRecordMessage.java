package com.callcenter.common.dto;

public record CallRecordMessage(
        Long callId,
        long tenantId,
        Long taskId,
        String phone,
        String lineNumber,
        Integer callStatus,
        Long startTime,
        Long endTime,
        Integer duration,
        Integer roundTotal,
        String recordingUrl,
        Integer errorCode,
        String errorDescription,
        Byte hangupBy,
        Byte connected,
        Long ringDuration,
        Long ringStartTime,
        Long hangupTime,
        String extJson
) {
}
