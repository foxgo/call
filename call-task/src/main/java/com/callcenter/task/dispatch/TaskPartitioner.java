package com.callcenter.task.dispatch;

final class TaskPartitioner {

    private final int partitionCount;

    TaskPartitioner(int partitionCount) {
        if (partitionCount <= 0) {
            throw new IllegalArgumentException("partitionCount must be positive");
        }
        this.partitionCount = partitionCount;
    }

    int partitionOf(Long taskId) {
        return Math.floorMod(Long.hashCode(taskId), partitionCount);
    }
}
