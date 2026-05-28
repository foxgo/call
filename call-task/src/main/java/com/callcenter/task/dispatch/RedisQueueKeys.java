package com.callcenter.task.dispatch;

public final class RedisQueueKeys {

    private RedisQueueKeys() {
    }

    public static String ready(Long taskId, int shard) {
        return "queue:ready:%d:%d".formatted(taskId, shard);
    }

    public static String processing(Long taskId, int shard) {
        return "queue:processing:%d:%d".formatted(taskId, shard);
    }

    public static String retry(Long taskId, int shard) {
        return "queue:retry:%d:%d".formatted(taskId, shard);
    }

    public static String retryDue(int partition) {
        return "queue:retry-due:%d".formatted(partition);
    }

    public static String processingTimeout(int partition) {
        return "queue:processing-timeout:%d".formatted(partition);
    }

    public static String retryDueMember(Long tenantId, Long taskId, int shard, Long dialUnitId) {
        return "%d:%d:%d:%d".formatted(tenantId, taskId, shard, dialUnitId);
    }

    public static String processingTimeoutMember(Long tenantId, Long taskId, int shard, Long dialUnitId) {
        return "%d:%d:%d:%d".formatted(tenantId, taskId, shard, dialUnitId);
    }
}
