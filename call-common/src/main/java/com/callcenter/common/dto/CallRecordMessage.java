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
        String extJson
) {
}

