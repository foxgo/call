package com.callcenter.task.model;

import com.callcenter.task.entity.CallTaskEntity;
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
        String callerIdMode,
        String optimizationGoal,
        Double answerWeight,
        Double conversionWeight,
        Double costWeight,
        Double riskWeight,
        Boolean localPresenceEnabled,
        Integer sameCallerCooldownSeconds,
        Integer maxCallerExposurePerHour,
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
                entity.getCallerIdMode(),
                entity.getOptimizationGoal(),
                entity.getAnswerWeight(),
                entity.getConversionWeight(),
                entity.getCostWeight(),
                entity.getRiskWeight(),
                entity.getLocalPresenceEnabled(),
                entity.getSameCallerCooldownSeconds(),
                entity.getMaxCallerExposurePerHour(),
                entity.getStartTime(),
                entity.getEndTime()
        );
    }
}
