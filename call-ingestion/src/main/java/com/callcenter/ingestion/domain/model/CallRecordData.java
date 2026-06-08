package com.callcenter.ingestion.domain.model;

import java.time.LocalDateTime;

public record CallRecordData(
        long callId,
        long tenantId,
        Long taskId,
        String phone,
        String lineNumber,
        Integer callStatus,
        Integer duration,
        Integer roundTotal,
        String recordingUrl,
        Integer errorCode,
        String errorDescription,
        Byte hangupBy,
        Byte connected,
        Long ringDuration,
        LocalDateTime ringStartTime,
        LocalDateTime hangupTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt
) {
}
