package com.callcenter.task.model;

import com.callcenter.task.repository.entity.CallTaskImportBatchEntity;
import java.time.LocalDateTime;

public record ImportBatchResponse(
        Long importBatchId,
        Long taskId,
        Long tenantId,
        String sourceType,
        String status,
        Integer totalCount,
        Integer successCount,
        Integer skippedCount,
        Integer failedCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ImportBatchResponse from(CallTaskImportBatchEntity entity) {
        return new ImportBatchResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getTenantId(),
                entity.getSourceType(),
                entity.getStatus(),
                entity.getTotalCount(),
                entity.getSuccessCount(),
                entity.getSkippedCount(),
                entity.getFailedCount(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
