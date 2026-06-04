package com.callcenter.search.model;

import java.util.List;

public record CallRecordDetailView(
        String callId,
        Long tenantId,
        Long taskId,
        String phone,
        String lineNumber,
        Integer callStatus,
        Integer duration,
        String recordingUrl,
        Integer errorCode,
        String errorDescription,
        Byte hangupBy,
        Byte connected,
        Long ringDuration,
        String ringStartTime,
        String hangupTime,
        String startTime,
        String endTime,
        String createdAt,
        String fullText,
        List<CallRoundView> rounds
) {
}
