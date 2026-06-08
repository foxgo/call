package com.callcenter.ingestion.domain.event;

import com.callcenter.ingestion.domain.model.CallRecordData;
import java.time.LocalDateTime;

public record CallRecordPersistedEvent(
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

    public static CallRecordPersistedEvent from(CallRecordData record) {
        return new CallRecordPersistedEvent(
                record.callId(),
                record.tenantId(),
                record.taskId(),
                record.phone(),
                record.lineNumber(),
                record.callStatus(),
                record.duration(),
                record.roundTotal(),
                record.recordingUrl(),
                record.errorCode(),
                record.errorDescription(),
                record.hangupBy(),
                record.connected(),
                record.ringDuration(),
                record.ringStartTime(),
                record.hangupTime(),
                record.startTime(),
                record.endTime(),
                record.createdAt()
        );
    }

    public CallRecordData toRecordData() {
        return new CallRecordData(
                callId,
                tenantId,
                taskId,
                phone,
                lineNumber,
                callStatus,
                duration,
                roundTotal,
                recordingUrl,
                errorCode,
                errorDescription,
                hangupBy,
                connected,
                ringDuration,
                ringStartTime,
                hangupTime,
                startTime,
                endTime,
                createdAt
        );
    }
}
