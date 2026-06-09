package com.callcenter.task.dispatch;

import com.callcenter.task.repository.entity.CallDialUnitEntity;
import com.callcenter.task.repository.entity.CallTaskEntity;
import com.callcenter.persistence.route.ShardKey;
import com.callcenter.task.repository.CallTaskRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultDispatchGateServiceTest {

    @Test
    void shouldRejectWhenTaskIsNotRunning() {
        CallTaskRepository repository = mock(CallTaskRepository.class);
        DefaultDispatchGateService service = new DefaultDispatchGateService(repository);
        when(repository.findRequired(9L, 1001L)).thenReturn(task("PAUSED"));

        DispatchGateDecision decision = service.evaluate(new ShardKey(9L, 0, 1, "dial"), unit());

        assertFalse(decision.allowed());
        assertEquals("TASK_NOT_RUNNING", decision.reason());
    }

    @Test
    void shouldAllowWhenTaskIsRunning() {
        CallTaskRepository repository = mock(CallTaskRepository.class);
        DefaultDispatchGateService service = new DefaultDispatchGateService(repository);
        when(repository.findRequired(9L, 1001L)).thenReturn(task("RUNNING"));

        DispatchGateDecision decision = service.evaluate(new ShardKey(9L, 0, 1, "dial"), unit());

        assertTrue(decision.allowed());
        assertEquals("ALLOW", decision.reason());
    }

    private static CallDialUnitEntity unit() {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setTenantId(9L);
        unit.setTaskId(1001L);
        return unit;
    }

    private static CallTaskEntity task(String status) {
        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);
        task.setStatus(status);
        return task;
    }
}
