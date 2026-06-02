package com.callcenter.task.service;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.model.CreateTaskRequest;
import com.callcenter.task.model.TaskSummaryResponse;
import com.callcenter.task.repository.CallTaskRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CallTaskServiceTest {

    @Test
    void shouldCreateTaskInInitStatus() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        when(idGenerator.nextId("9")).thenReturn(1001L);
        CallTaskService service = new CallTaskService(taskRepository, idGenerator, activationService);

        CreateTaskRequest request = new CreateTaskRequest();
        request.setName("task-a");
        request.setPriority(1);
        request.setMaxConcurrency(8);

        TaskSummaryResponse response = service.createTask(9L, request);
        ArgumentCaptor<CallTaskEntity> captor = ArgumentCaptor.forClass(CallTaskEntity.class);

        assertEquals("INIT", response.status());
        assertEquals(1001L, response.taskId());
        assertEquals(1, response.priority());
        assertEquals("HYBRID", response.callerIdMode());
        assertEquals("ANSWER", response.optimizationGoal());
        assertEquals(1D, response.answerWeight());
        verify(taskRepository).insert(captor.capture());
        assertEquals("HYBRID", captor.getValue().getCallerIdMode());
        assertEquals("ANSWER", captor.getValue().getOptimizationGoal());
        assertEquals(3600, captor.getValue().getSameCallerCooldownSeconds());
        verify(activationService, never()).activate(9L, 1001L);
    }

    @Test
    void shouldPersistExplicitCallerPolicyFieldsOnCreate() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        when(idGenerator.nextId("9")).thenReturn(1002L);
        CallTaskService service = new CallTaskService(taskRepository, idGenerator, activationService);

        CreateTaskRequest request = new CreateTaskRequest();
        request.setName("task-b");
        request.setPriority(2);
        request.setMaxConcurrency(5);
        request.setCallerIdMode("TASK_ONLY");
        request.setOptimizationGoal("RISK");
        request.setAnswerWeight(0.3D);
        request.setConversionWeight(0.2D);
        request.setCostWeight(0.1D);
        request.setRiskWeight(0.9D);
        request.setLocalPresenceEnabled(true);
        request.setSameCallerCooldownSeconds(120);
        request.setMaxCallerExposurePerHour(12);

        service.createTask(9L, request);

        ArgumentCaptor<CallTaskEntity> captor = ArgumentCaptor.forClass(CallTaskEntity.class);
        verify(taskRepository).insert(captor.capture());
        assertEquals("TASK_ONLY", captor.getValue().getCallerIdMode());
        assertEquals("RISK", captor.getValue().getOptimizationGoal());
        assertEquals(0.3D, captor.getValue().getAnswerWeight());
        assertEquals(0.2D, captor.getValue().getConversionWeight());
        assertEquals(0.1D, captor.getValue().getCostWeight());
        assertEquals(0.9D, captor.getValue().getRiskWeight());
        assertEquals(true, captor.getValue().getLocalPresenceEnabled());
        assertEquals(120, captor.getValue().getSameCallerCooldownSeconds());
        assertEquals(12, captor.getValue().getMaxCallerExposurePerHour());
    }

    @Test
    void shouldActivateTaskAfterStartTransition() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskEntity entity = new CallTaskEntity();
        entity.setId(1001L);
        entity.setTenantId(9L);
        entity.setStatus("INIT");
        when(taskRepository.findRequired(9L, 1001L)).thenReturn(entity);
        CallTaskService service = new CallTaskService(taskRepository, idGenerator, activationService);

        TaskSummaryResponse response = service.startTask(9L, 1001L);

        assertEquals("RUNNING", response.status());
        verify(taskRepository).updateById(entity);
        verify(activationService).activate(9L, 1001L);
    }

    @Test
    void shouldActivateTaskAfterResumeTransition() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallTaskEntity entity = new CallTaskEntity();
        entity.setId(1001L);
        entity.setTenantId(9L);
        entity.setStatus("PAUSED");
        when(taskRepository.findRequired(9L, 1001L)).thenReturn(entity);
        CallTaskService service = new CallTaskService(taskRepository, idGenerator, activationService);

        TaskSummaryResponse response = service.resumeTask(9L, 1001L);

        assertEquals("RUNNING", response.status());
        verify(taskRepository).updateById(entity);
        verify(activationService).activate(9L, 1001L);
    }
}
