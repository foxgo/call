package com.callcenter.task.dispatch;

record TaskActivationRequest(
        Long tenantId,
        Long taskId,
        int priority,
        int weight,
        int partition
) {
}
