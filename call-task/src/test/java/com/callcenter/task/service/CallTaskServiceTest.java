package com.callcenter.task.service;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.model.CreateTaskRequest;
import com.callcenter.task.model.TaskSummaryResponse;
import com.callcenter.task.repository.CallTaskRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTaskServiceTest {

    @Test
    void shouldCreateTaskInInitStatus() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        when(idGenerator.nextId("9")).thenReturn(1001L);
        CallTaskService service = new CallTaskService(taskRepository, idGenerator);

        CreateTaskRequest request = new CreateTaskRequest();
        request.setName("task-a");
        request.setMaxConcurrency(8);

        TaskSummaryResponse response = service.createTask(9L, request);

        assertEquals("INIT", response.status());
        assertEquals(1001L, response.taskId());
        verify(taskRepository).insert(any(CallTaskEntity.class));
    }
}
