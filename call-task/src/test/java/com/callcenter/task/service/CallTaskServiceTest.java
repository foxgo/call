package com.callcenter.task.service;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.model.CreateTaskRequest;
import com.callcenter.task.model.TaskSummaryResponse;
import com.callcenter.task.repository.CallTaskRepository;
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

        assertEquals("INIT", response.status());
        assertEquals(1001L, response.taskId());
        assertEquals(1, response.priority());
        verify(taskRepository).insert(any(CallTaskEntity.class));
        verify(activationService, never()).activate(9L, 1001L);
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
