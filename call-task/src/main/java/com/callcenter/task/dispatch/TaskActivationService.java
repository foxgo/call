package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.task.repository.CallTaskRepository;
import org.springframework.stereotype.Component;

@Component
public class TaskActivationService {

    private static final int DEFAULT_PRIORITY = 4;

    private final ActiveTaskQueue activeTaskQueue;
    private final CallTaskRepository callTaskRepository;
    private final TaskPartitioner taskPartitioner;

    public TaskActivationService(
            ActiveTaskQueue activeTaskQueue,
            CallTaskRepository callTaskRepository,
            com.callcenter.task.config.CallTaskDispatchProperties properties
    ) {
        this.activeTaskQueue = activeTaskQueue;
        this.callTaskRepository = callTaskRepository;
        this.taskPartitioner = new TaskPartitioner(properties.getPartitionCount());
    }

    TaskActivationService(ActiveTaskQueue activeTaskQueue) {
        this(activeTaskQueue, null, new com.callcenter.task.config.CallTaskDispatchProperties());
    }

    TaskActivationService(ActiveTaskQueue activeTaskQueue, CallTaskRepository callTaskRepository) {
        this(activeTaskQueue, callTaskRepository, new com.callcenter.task.config.CallTaskDispatchProperties());
    }

    public void activate(TaskActivationRequest request) {
        activeTaskQueue.upsertMeta(
                request.taskId(),
                request.tenantId(),
                request.priority(),
                request.weight(),
                request.partition(),
                0L
        );
        activeTaskQueue.activate(request.partition(), request.tenantId(), request.taskId(), 0L);
    }

    public void activate(Long tenantId, Long taskId) {
        int priority = resolvePriority(tenantId, taskId);
        activate(new TaskActivationRequest(
                tenantId,
                taskId,
                priority,
                TaskPriorityWeight.fromPriority(priority),
                taskPartitioner.partitionOf(taskId)
        ));
    }

    private int resolvePriority(Long tenantId, Long taskId) {
        if (callTaskRepository == null) {
            return DEFAULT_PRIORITY;
        }
        CallTaskEntity task = callTaskRepository.findRequired(tenantId, taskId);
        return task.getPriority() == null ? DEFAULT_PRIORITY : task.getPriority();
    }
}
