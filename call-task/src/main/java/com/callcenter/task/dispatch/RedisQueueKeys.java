package com.callcenter.task.dispatch;

public final class RedisQueueKeys {

    private RedisQueueKeys() {
    }

    public static String ready(Long taskId, int shard) {
        return "queue:ready:%d:%d".formatted(taskId, shard);
    }
}
