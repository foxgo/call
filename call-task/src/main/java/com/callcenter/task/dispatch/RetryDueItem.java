package com.callcenter.task.dispatch;

record RetryDueItem(Long tenantId, Long taskId, int shard, Long dialUnitId) {

    static RetryDueItem parse(String value) {
        String[] parts = value.split(":");
        return new RetryDueItem(
                Long.parseLong(parts[0]),
                Long.parseLong(parts[1]),
                Integer.parseInt(parts[2]),
                Long.parseLong(parts[3])
        );
    }
}
