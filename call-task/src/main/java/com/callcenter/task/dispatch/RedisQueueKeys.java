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
}
