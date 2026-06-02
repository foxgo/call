package com.callcenter.task.dispatch;

public final class RedisQueueKeys {

    private RedisQueueKeys() {
    }

    public static String taskRef(int dbIndex, Long taskId) {
        return "%d:%d".formatted(dbIndex, taskId);
    }

    public static Long taskId(String taskRef) {
        return Long.parseLong(taskRef.substring(taskRef.indexOf(':') + 1));
    }

    public static String ready(int dbIndex, Long taskId) {
        return "queue:ready:%d:%d".formatted(dbIndex, taskId);
    }
}
