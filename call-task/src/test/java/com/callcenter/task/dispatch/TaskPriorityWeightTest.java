package com.callcenter.task.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskPriorityWeightTest {

    @Test
    void shouldMapPriorityToStableWeight() {
        assertEquals(16, TaskPriorityWeight.fromPriority(1));
        assertEquals(8, TaskPriorityWeight.fromPriority(2));
        assertEquals(4, TaskPriorityWeight.fromPriority(3));
        assertEquals(2, TaskPriorityWeight.fromPriority(4));
        assertEquals(2, TaskPriorityWeight.fromPriority(99));
    }
}
