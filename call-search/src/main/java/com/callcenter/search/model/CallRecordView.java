package com.callcenter.search.model;

import java.util.List;

public record CallRecordView(
        String callId,
        Long tenantId,
        Long taskId,
        String phone,
        String lineNumber,
        Integer callStatus,
        Integer duration,
        String startTime,
        String endTime,
        String createdAt,
        List<String> highlights
) {
}
