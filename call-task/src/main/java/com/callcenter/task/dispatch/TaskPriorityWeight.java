package com.callcenter.task.dispatch;

final class TaskPriorityWeight {

    private TaskPriorityWeight() {
    }

    static int fromPriority(int priority) {
        return switch (priority) {
            case 1 -> 16;
            case 2 -> 8;
            case 3 -> 4;
            default -> 2;
        };
    }
}
