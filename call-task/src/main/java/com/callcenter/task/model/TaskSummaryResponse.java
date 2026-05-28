package com.callcenter.task.model;

import com.callcenter.common.entity.CallTaskEntity;
import java.time.LocalDateTime;

public record TaskSummaryResponse(
        Long taskId,
        Long tenantId,
        String name,
        String status,
        Integer totalCount,
        Integer queuedCount,
        Integer dialingCount,
        Integer successCount,
        Integer failedCount,
        Integer priority,
        Integer maxConcurrency,
        LocalDateTime startTime,
        LocalDateTime endTime
) {

    public static TaskSummaryResponse from(CallTaskEntity entity) {
        return new TaskSummaryResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getStatus(),
                entity.getTotalCount(),
                entity.getQueuedCount(),
                entity.getDialingCount(),
                entity.getSuccessCount(),
                entity.getFailedCount(),
                entity.getPriority(),
                entity.getMaxConcurrency(),
                entity.getStartTime(),
                entity.getEndTime()
        );
    }
}
