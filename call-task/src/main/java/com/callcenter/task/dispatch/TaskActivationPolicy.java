package com.callcenter.task.dispatch;

final class TaskActivationPolicy {

    private TaskActivationPolicy() {
    }

    static boolean shouldActivate(TaskBlockReason blockedReason, boolean hasReadyUnits, boolean hasAvailableCapacity) {
        if (blockedReason == TaskBlockReason.PAUSED) {
            return false;
        }
        return hasReadyUnits && hasAvailableCapacity;
    }
}
