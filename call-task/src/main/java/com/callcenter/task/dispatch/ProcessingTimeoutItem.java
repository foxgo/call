package com.callcenter.task.dispatch;

record ProcessingTimeoutItem(Long tenantId, Long taskId, int shard, Long dialUnitId) {

    static ProcessingTimeoutItem parse(String value) {
        String[] parts = value.split(":");
        return new ProcessingTimeoutItem(
                Long.parseLong(parts[0]),
                Long.parseLong(parts[1]),
                Integer.parseInt(parts[2]),
                Long.parseLong(parts[3])
        );
    }
}
