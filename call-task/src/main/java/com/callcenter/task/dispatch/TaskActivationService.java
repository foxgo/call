package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.task.repository.CallTaskRepository;
import org.springframework.stereotype.Component;

/**
 * 把可运行任务注册到活跃任务队列。
 * 这里不直接做调度，只负责把任务按优先级和分区送入后续 dispatcher。
 */
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
        // fairScore 初始为 0，后续由调度器按已分发量累加，形成简单的加权公平调度。
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
        // 激活时实时回库读取优先级，保证管理端修改后的优先级能立即生效。
        CallTaskEntity task = callTaskRepository.findRequired(tenantId, taskId);
        return task.getPriority() == null ? DEFAULT_PRIORITY : task.getPriority();
    }
}
