package com.callcenter.task.caller;

import com.callcenter.task.entity.CallTaskEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskCallerIdPolicyServiceTest {

    @Test
    void shouldUseDefaultsWhenTaskPolicyFieldsAreMissing() {
        CallTaskEntity task = new CallTaskEntity();

        TaskCallerIdPolicy policy = new TaskCallerIdPolicyService().toPolicy(task);

        assertEquals("HYBRID", policy.callerIdMode());
        assertEquals("ANSWER", policy.optimizationGoal());
        assertEquals(1D, policy.answerWeight());
        assertEquals(200, policy.maxCallerExposurePerHour());
        assertFalse(policy.localPresenceEnabled());
    }

    @Test
    void shouldMapExplicitTaskPolicyFields() {
        CallTaskEntity task = new CallTaskEntity();
        task.setCallerIdMode("TASK_ONLY");
        task.setOptimizationGoal("RISK");
        task.setAnswerWeight(0.2D);
        task.setConversionWeight(0.3D);
        task.setCostWeight(0.4D);
        task.setRiskWeight(0.9D);
        task.setLocalPresenceEnabled(true);
        task.setSameCallerCooldownSeconds(90);
        task.setMaxCallerExposurePerHour(11);

        TaskCallerIdPolicy policy = new TaskCallerIdPolicyService().toPolicy(task);

        assertEquals("TASK_ONLY", policy.callerIdMode());
        assertEquals("RISK", policy.optimizationGoal());
        assertEquals(0.2D, policy.answerWeight());
        assertEquals(0.3D, policy.conversionWeight());
        assertEquals(0.4D, policy.costWeight());
        assertEquals(0.9D, policy.riskWeight());
        assertTrue(policy.localPresenceEnabled());
        assertEquals(90, policy.sameCallerCooldownSeconds());
        assertEquals(11, policy.maxCallerExposurePerHour());
    }

    @Test
    void shouldMapRetryCountToAttemptStage() {
        assertEquals(AttemptStage.FIRST_ATTEMPT, AttemptStage.fromRetryCount(0));
        assertEquals(AttemptStage.FIRST_ATTEMPT, AttemptStage.fromRetryCount(null));
        assertEquals(AttemptStage.RETRY_ATTEMPT, AttemptStage.fromRetryCount(1));
    }
}
