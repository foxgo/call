package com.callcenter.task.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskActivationPolicyTest {

    @Test
    void shouldReactivateTaskWhenCapacityReturns() {
        assertTrue(TaskActivationPolicy.shouldActivate(
                TaskBlockReason.CONCURRENCY_FULL,
                true,
                true
        ));
    }

    @Test
    void shouldNotActivatePausedTask() {
        assertFalse(TaskActivationPolicy.shouldActivate(
                TaskBlockReason.PAUSED,
                true,
                true
        ));
    }
}
