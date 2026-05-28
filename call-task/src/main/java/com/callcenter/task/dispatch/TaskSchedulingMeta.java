package com.callcenter.task.dispatch;

record TaskSchedulingMeta(
        Long taskId,
        Long tenantId,
        int priority,
        int weight,
        int partition,
        long fairScore,
        TaskSchedulingState state,
        TaskBlockReason blockedReason
) {
}
