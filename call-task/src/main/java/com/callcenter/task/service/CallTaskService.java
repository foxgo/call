package com.callcenter.task.service;

import com.callcenter.task.entity.CallTaskEntity;
import com.callcenter.task.enums.CallTaskStatus;
import com.callcenter.persistence.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.model.CreateTaskRequest;
import com.callcenter.task.model.TaskSummaryResponse;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务生命周期入口服务。
 * 负责创建任务、校验状态迁移，并在任务进入运行态时接入调度链路。
 */
@Service
public class CallTaskService {

    private final CallTaskRepository callTaskRepository;
    private final ShardedSnowflakeIdGenerator idGenerator;
    private final TaskActivationService taskActivationService;

    public CallTaskService(
            CallTaskRepository callTaskRepository,
            ShardedSnowflakeIdGenerator idGenerator,
            TaskActivationService taskActivationService
    ) {
        this.callTaskRepository = callTaskRepository;
        this.idGenerator = idGenerator;
        this.taskActivationService = taskActivationService;
    }

    @Transactional
    public TaskSummaryResponse createTask(Long tenantId, CreateTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        CallTaskEntity entity = new CallTaskEntity();
        entity.setId(idGenerator.nextId(String.valueOf(tenantId)));
        entity.setTenantId(tenantId);
        entity.setName(request.getName());
        entity.setStatus(CallTaskStatus.INIT.name());
        entity.setTotalCount(0);
        entity.setQueuedCount(0);
        entity.setDialingCount(0);
        entity.setSuccessCount(0);
        entity.setFailedCount(0);
        // 任务创建时先补齐策略参数默认值，避免后续调度链路出现空值分支。
        entity.setPriority(request.getPriority());
        entity.setMaxConcurrency(request.getMaxConcurrency());
        entity.setCallerIdMode(defaultString(request.getCallerIdMode(), "HYBRID"));
        entity.setOptimizationGoal(defaultString(request.getOptimizationGoal(), "ANSWER"));
        entity.setAnswerWeight(defaultDouble(request.getAnswerWeight(), 1D));
        entity.setConversionWeight(defaultDouble(request.getConversionWeight(), 0D));
        entity.setCostWeight(defaultDouble(request.getCostWeight(), 0D));
        entity.setRiskWeight(defaultDouble(request.getRiskWeight(), 0D));
        entity.setLocalPresenceEnabled(request.getLocalPresenceEnabled() != null && request.getLocalPresenceEnabled());
        entity.setSameCallerCooldownSeconds(defaultInteger(request.getSameCallerCooldownSeconds(), 3600));
        entity.setMaxCallerExposurePerHour(defaultInteger(request.getMaxCallerExposurePerHour(), 200));
        entity.setStartTime(request.getStartTime());
        entity.setVersion(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        callTaskRepository.insert(entity);
        return TaskSummaryResponse.from(entity);
    }

    @Transactional
    public TaskSummaryResponse startTask(Long tenantId, Long taskId) {
        return updateStatus(tenantId, taskId, CallTaskStatus.INIT, CallTaskStatus.RUNNING);
    }

    @Transactional
    public TaskSummaryResponse pauseTask(Long tenantId, Long taskId) {
        return updateStatus(tenantId, taskId, CallTaskStatus.RUNNING, CallTaskStatus.PAUSED);
    }

    @Transactional
    public TaskSummaryResponse resumeTask(Long tenantId, Long taskId) {
        return updateStatus(tenantId, taskId, CallTaskStatus.PAUSED, CallTaskStatus.RUNNING);
    }

    public TaskSummaryResponse getTask(Long tenantId, Long taskId) {
        return TaskSummaryResponse.from(loadTask(tenantId, taskId));
    }

    public List<TaskSummaryResponse> listTasks(Long tenantId) {
        return callTaskRepository.listByTenant(tenantId).stream().map(TaskSummaryResponse::from).toList();
    }

    private TaskSummaryResponse updateStatus(
            Long tenantId,
            Long taskId,
            CallTaskStatus expectedStatus,
            CallTaskStatus targetStatus
    ) {
        CallTaskEntity entity = loadTask(tenantId, taskId);
        if (!expectedStatus.name().equals(entity.getStatus())) {
            throw new IllegalStateException(
                    "Task status transition not allowed: %s -> %s".formatted(entity.getStatus(), targetStatus.name())
            );
        }
        // 先持久化状态，再激活调度，避免调度线程读取到旧状态后又把任务重新挂起。
        entity.setStatus(targetStatus.name());
        entity.setUpdatedAt(LocalDateTime.now());
        callTaskRepository.updateById(entity);
        if (targetStatus == CallTaskStatus.RUNNING) {
            taskActivationService.activate(tenantId, taskId);
        }
        return TaskSummaryResponse.from(entity);
    }

    private CallTaskEntity loadTask(Long tenantId, Long taskId) {
        return callTaskRepository.findRequired(tenantId, taskId);
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Double defaultDouble(Double value, Double fallback) {
        return value == null ? fallback : value;
    }

    private static Integer defaultInteger(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }
}
