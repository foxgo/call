package com.callcenter.task.mq;

public record DialDispatchMessage(
        Long dialUnitId,
        Long tenantId,
        Long taskId,
        String phone,
        String dispatchToken
) {
}
