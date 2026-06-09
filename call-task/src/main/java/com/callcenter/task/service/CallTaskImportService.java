package com.callcenter.task.service;

import com.callcenter.task.entity.CallDialUnitEntity;
import com.callcenter.task.entity.CallTaskEntity;
import com.callcenter.task.entity.CallTaskImportBatchEntity;
import com.callcenter.task.enums.CallDialUnitStatus;
import com.callcenter.task.enums.CallTaskStatus;
import com.callcenter.task.enums.CallTaskImportBatchStatus;
import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.persistence.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.model.ImportBatchResponse;
import com.callcenter.task.model.ImportDialUnitItem;
import com.callcenter.task.model.ImportDialUnitsRequest;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskImportBatchRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallTaskImportService {

    private final CallTaskRepository callTaskRepository;
    private final CallTaskImportBatchRepository importBatchRepository;
    private final CallDialUnitRepository callDialUnitRepository;
    private final ShardedSnowflakeIdGenerator idGenerator;
    private final ShardingRouter shardingRouter;
    private final TaskActivationService taskActivationService;

    public CallTaskImportService(
            CallTaskRepository callTaskRepository,
            CallTaskImportBatchRepository importBatchRepository,
            CallDialUnitRepository callDialUnitRepository,
            ShardedSnowflakeIdGenerator idGenerator,
            ShardingRouter shardingRouter,
            TaskActivationService taskActivationService
    ) {
        this.callTaskRepository = callTaskRepository;
        this.importBatchRepository = importBatchRepository;
        this.callDialUnitRepository = callDialUnitRepository;
        this.idGenerator = idGenerator;
        this.shardingRouter = shardingRouter;
        this.taskActivationService = taskActivationService;
    }

    @Transactional
    public ImportBatchResponse importDialUnits(Long tenantId, Long taskId, ImportDialUnitsRequest request) {
        CallTaskEntity task = loadTask(tenantId, taskId);
        LocalDateTime now = LocalDateTime.now();

        CallTaskImportBatchEntity batch = new CallTaskImportBatchEntity();
        batch.setId(idGenerator.nextId(taskId + ":import"));
        batch.setTenantId(tenantId);
        batch.setTaskId(taskId);
        batch.setSourceType(request.getSourceType());
        batch.setStatus(CallTaskImportBatchStatus.PROCESSING.name());
        batch.setTotalCount(request.getUnits().size());
        batch.setSuccessCount(0);
        batch.setSkippedCount(0);
        batch.setFailedCount(0);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        importBatchRepository.insert(batch);

        List<CallDialUnitEntity> entities = request.getUnits().stream()
                .map(item -> toDialUnit(tenantId, taskId, batch.getId(), now, item))
                .toList();

        ShardKey shardKey = shardingRouter.routeDialUnit(tenantId, taskId);
        int insertedCount = callDialUnitRepository.batchInsert(shardKey, entities);
        int skippedCount = Math.max(entities.size() - insertedCount, 0);

        batch.setStatus(CallTaskImportBatchStatus.COMPLETED.name());
        batch.setSuccessCount(insertedCount);
        batch.setSkippedCount(skippedCount);
        batch.setUpdatedAt(LocalDateTime.now());
        importBatchRepository.updateById(batch);

        task.setTotalCount((task.getTotalCount() == null ? 0 : task.getTotalCount()) + insertedCount);
        task.setUpdatedAt(LocalDateTime.now());
        callTaskRepository.updateById(task);
        if (CallTaskStatus.RUNNING.name().equals(task.getStatus())) {
            taskActivationService.activate(tenantId, taskId);
        }
        return ImportBatchResponse.from(batch);
    }

    public ImportBatchResponse getImportBatch(Long tenantId, Long taskId, Long importBatchId) {
        return ImportBatchResponse.from(importBatchRepository.findRequired(tenantId, taskId, importBatchId));
    }

    private CallTaskEntity loadTask(Long tenantId, Long taskId) {
        return callTaskRepository.findRequired(tenantId, taskId);
    }

    private CallDialUnitEntity toDialUnit(
            Long tenantId,
            Long taskId,
            Long importBatchId,
            LocalDateTime now,
            ImportDialUnitItem item
    ) {
        CallDialUnitEntity entity = new CallDialUnitEntity();
        entity.setId(idGenerator.nextId(item.getPhone()));
        entity.setTenantId(tenantId);
        entity.setTaskId(taskId);
        entity.setImportBatchId(importBatchId);
        entity.setPhone(item.getPhone());
        entity.setStatus(CallDialUnitStatus.PENDING.name());
        entity.setRetryCount(0);
        entity.setMaxRetryCount(item.getMaxRetryCount() == null ? 3 : item.getMaxRetryCount());
        entity.setScore(item.getScore() == null ? 0F : item.getScore());
        entity.setBizIdempotencyKey(item.getBizIdempotencyKey() == null ? "" : item.getBizIdempotencyKey());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
