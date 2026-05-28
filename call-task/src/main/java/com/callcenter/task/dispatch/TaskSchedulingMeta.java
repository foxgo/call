package com.callcenter.task.dispatch;

record TaskSchedulingMeta(
        Long taskId,
        Long tenantId,
        int priority,
        int weight,
        int partition,
        TaskSchedulingState state,
        TaskBlockReason blockedReason
) {
}
