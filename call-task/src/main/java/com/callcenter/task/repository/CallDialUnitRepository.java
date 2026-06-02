package com.callcenter.task.repository;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.callcenter.common.context.ShardContextHolder;
import com.callcenter.common.config.ShardProperties;
import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.enums.CallDialUnitStatus;
import com.callcenter.common.mapper.CallDialUnitMapper;
import com.callcenter.common.route.ShardKey;
import com.callcenter.task.model.RetryDecision;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class CallDialUnitRepository {

    private final CallDialUnitMapper callDialUnitMapper;
    private final ShardProperties shardProperties;

    public CallDialUnitRepository(CallDialUnitMapper callDialUnitMapper, ShardProperties shardProperties) {
        this.callDialUnitMapper = callDialUnitMapper;
        this.shardProperties = shardProperties;
    }

    public int batchInsert(ShardKey shardKey, List<CallDialUnitEntity> entities) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            return callDialUnitMapper.batchInsertIgnore(entities);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public List<CallDialUnitEntity> listByTaskId(ShardKey shardKey, long taskId) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId).orderByAsc("id");
            return callDialUnitMapper.selectList(query);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public List<CallDialUnitEntity> listByTaskIdAndIds(ShardKey shardKey, long taskId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId)
                    .in("id", ids)
                    .orderByAsc("id");
            return callDialUnitMapper.selectList(query);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public long countRemainingDialUnits(ShardKey shardKey, long taskId) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId)
                    .in("status", CallDialUnitStatus.PENDING.name(), CallDialUnitStatus.READY.name());
            Long count = callDialUnitMapper.selectCount(query);
            return count == null ? 0L : count;
        } finally {
            ShardContextHolder.clear();
        }
    }

    public List<DuePendingTask> findDuePendingTasks(LocalDateTime now, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Map<String, DuePendingTask> tasks = new LinkedHashMap<>();
        for (ShardKey shardKey : allDialShards()) {
            if (tasks.size() >= limit) {
                break;
            }
            ShardContextHolder.set(shardKey.toContext());
            try {
                QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
                query.select("tenant_id", "task_id")
                        .eq("status", CallDialUnitStatus.PENDING.name())
                        .le("next_call_time", now)
                        .orderByAsc("next_call_time")
                        .orderByAsc("id")
                        .last("LIMIT " + limit);
                List<CallDialUnitEntity> units = callDialUnitMapper.selectList(query);
                for (CallDialUnitEntity unit : units) {
                    String key = unit.getTenantId() + ":" + unit.getTaskId();
                    tasks.putIfAbsent(key, new DuePendingTask(unit.getTenantId(), unit.getTaskId()));
                    if (tasks.size() >= limit) {
                        break;
                    }
                }
            } finally {
                ShardContextHolder.clear();
            }
        }
        return List.copyOf(tasks.values());
    }

    public List<ExpiredDialingBatch> findExpiredDialingBatches(LocalDateTime now, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Map<String, ExpiredDialingBatchBuilder> batches = new LinkedHashMap<>();
        int collected = 0;
        for (ShardKey shardKey : allDialShards()) {
            if (collected >= limit) {
                break;
            }
            ShardContextHolder.set(shardKey.toContext());
            try {
                QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
                query.select("id", "tenant_id", "task_id")
                        .eq("status", CallDialUnitStatus.DIALING.name())
                        .le("inflight_expire_at", now)
                        .orderByAsc("inflight_expire_at")
                        .orderByAsc("id")
                        .last("LIMIT " + limit);
                List<CallDialUnitEntity> units = callDialUnitMapper.selectList(query);
                for (CallDialUnitEntity unit : units) {
                    String key = unit.getTenantId() + ":" + unit.getTaskId();
                    ExpiredDialingBatchBuilder batch = batches.computeIfAbsent(
                            key,
                            ignored -> new ExpiredDialingBatchBuilder(unit.getTenantId(), unit.getTaskId())
                    );
                    batch.dialUnitIds().add(unit.getId());
                    collected++;
                    if (collected >= limit) {
                        break;
                    }
                }
            } finally {
                ShardContextHolder.clear();
            }
        }
        return batches.values().stream()
                .map(builder -> new ExpiredDialingBatch(builder.tenantId(), builder.taskId(), List.copyOf(builder.dialUnitIds())))
                .toList();
    }

    public List<CallDialUnitEntity> claimPendingToReady(ShardKey shardKey, long taskId, int limit, LocalDateTime now) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId)
                    .eq("status", CallDialUnitStatus.PENDING.name())
                    .le("next_call_time", now)
                    .orderByAsc("next_call_time")
                    .orderByAsc("id")
                    .last("LIMIT " + limit);
            List<CallDialUnitEntity> pending = callDialUnitMapper.selectList(query);
            List<Long> ids = pending.stream().map(CallDialUnitEntity::getId).toList();
            if (ids.isEmpty()) {
                return List.of();
            }
            UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
            update.eq("task_id", taskId)
                    .in("id", ids)
                    .eq("status", CallDialUnitStatus.PENDING.name())
                    .le("next_call_time", now)
                    .set("status", CallDialUnitStatus.READY.name())
                    .set("updated_at", now);
            callDialUnitMapper.update(null, update);

            QueryWrapper<CallDialUnitEntity> readyQuery = new QueryWrapper<>();
            readyQuery.eq("task_id", taskId)
                    .in("id", ids)
                    .eq("status", CallDialUnitStatus.READY.name())
                    .orderByAsc("id");
            return callDialUnitMapper.selectList(readyQuery);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public List<CallDialUnitEntity> markDialingFromReady(
            ShardKey shardKey,
            long taskId,
            List<Long> ids,
            String dispatchToken,
            LocalDateTime callTime,
            LocalDateTime inflightExpireAt
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId).in("id", ids).orderByAsc("id");
            List<CallDialUnitEntity> units = callDialUnitMapper.selectList(query);
            List<Long> readyIds = units.stream().map(CallDialUnitEntity::getId).toList();
            if (readyIds.isEmpty()) {
                return List.of();
            }
            UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
            update.eq("task_id", taskId)
                    .in("id", readyIds)
                    .eq("status", CallDialUnitStatus.READY.name())
                    .set("status", CallDialUnitStatus.DIALING.name())
                    .set("dispatch_token", dispatchToken)
                    .set("last_call_time", callTime)
                    .set("inflight_expire_at", inflightExpireAt)
                    .set("updated_at", callTime);
            callDialUnitMapper.update(null, update);

            QueryWrapper<CallDialUnitEntity> dialingQuery = new QueryWrapper<>();
            dialingQuery.eq("task_id", taskId)
                    .in("id", readyIds)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken)
                    .orderByAsc("id");
            return callDialUnitMapper.selectList(dialingQuery);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public List<CallDialUnitEntity> markDialingSelectionsFromReady(
            ShardKey shardKey,
            long taskId,
            List<CallDialUnitEntity> units,
            LocalDateTime callTime,
            LocalDateTime inflightExpireAt
    ) {
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        ShardContextHolder.set(shardKey.toContext());
        try {
            List<CallDialUnitEntity> transitioned = new ArrayList<>();
            for (CallDialUnitEntity unit : units) {
                UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
                update.eq("task_id", taskId)
                        .eq("id", unit.getId())
                        .eq("status", CallDialUnitStatus.READY.name())
                        .set("status", CallDialUnitStatus.DIALING.name())
                        .set("dispatch_token", unit.getDispatchToken())
                        .set("selected_caller_id", unit.getSelectedCallerId())
                        .set("caller_id_selection_score", unit.getCallerIdSelectionScore())
                        .set("caller_id_selection_reason", unit.getCallerIdSelectionReason())
                        .set("attempt_stage", unit.getAttemptStage())
                        .set("last_call_time", callTime)
                        .set("inflight_expire_at", inflightExpireAt)
                        .set("updated_at", callTime);
                if (callDialUnitMapper.update(null, update) <= 0) {
                    continue;
                }
                QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
                query.eq("task_id", taskId)
                        .eq("id", unit.getId())
                        .eq("status", CallDialUnitStatus.DIALING.name())
                        .eq("dispatch_token", unit.getDispatchToken())
                        .last("LIMIT 1");
                CallDialUnitEntity updated = callDialUnitMapper.selectOne(query);
                if (updated != null) {
                    updated.setSelectedCallerNumber(unit.getSelectedCallerNumber());
                    transitioned.add(updated);
                }
            }
            return transitioned;
        } finally {
            ShardContextHolder.clear();
        }
    }

    public boolean markSuccess(ShardKey shardKey, long taskId, long dialUnitId, String dispatchToken) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
            update.eq("task_id", taskId)
                    .eq("id", dialUnitId)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken)
                    .set("status", CallDialUnitStatus.SUCCESS.name())
                    .set("updated_at", LocalDateTime.now());
            return callDialUnitMapper.update(null, update) > 0;
        } finally {
            ShardContextHolder.clear();
        }
    }

    public CallDialUnitEntity findDialingByDispatchToken(
            ShardKey shardKey,
            long taskId,
            long dialUnitId,
            String dispatchToken
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId)
                    .eq("id", dialUnitId)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken)
                    .last("LIMIT 1");
            return callDialUnitMapper.selectOne(query);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public boolean markSuccess(
            ShardKey shardKey,
            long taskId,
            long dialUnitId,
            String dispatchToken,
            Integer ringDurationSeconds,
            Integer talkDurationSeconds,
            String hangupCode
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
            update.eq("task_id", taskId)
                    .eq("id", dialUnitId)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken)
                    .set("status", CallDialUnitStatus.SUCCESS.name())
                    .set("ring_duration_seconds", ringDurationSeconds)
                    .set("talk_duration_seconds", talkDurationSeconds)
                    .set("hangup_code", hangupCode)
                    .set("updated_at", LocalDateTime.now());
            return callDialUnitMapper.update(null, update) > 0;
        } finally {
            ShardContextHolder.clear();
        }
    }

    public RetryDecision markFailedForRetry(
            ShardKey shardKey,
            long taskId,
            long dialUnitId,
            String dispatchToken,
            String failureCode,
            String failureReason,
            java.time.Instant retryAt,
            Integer ringDurationSeconds,
            Integer talkDurationSeconds,
            String hangupCode
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId)
                    .eq("id", dialUnitId)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken);
            CallDialUnitEntity unit = callDialUnitMapper.selectOne(query);
            if (unit == null) {
                return RetryDecision.stale();
            }

            LocalDateTime now = LocalDateTime.now();
            int nextRetryCount = (unit.getRetryCount() == null ? 0 : unit.getRetryCount()) + 1;
            boolean shouldRetry = nextRetryCount <= (unit.getMaxRetryCount() == null ? 0 : unit.getMaxRetryCount());

            UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
            update.eq("task_id", taskId)
                    .eq("id", dialUnitId)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken)
                    .set("retry_count", nextRetryCount)
                    .set("failure_code", failureCode)
                    .set("failure_reason", failureReason)
                    .set("ring_duration_seconds", ringDurationSeconds)
                    .set("talk_duration_seconds", talkDurationSeconds)
                    .set("hangup_code", hangupCode)
                    .set("updated_at", now);
            if (shouldRetry) {
                update.set("status", CallDialUnitStatus.PENDING.name())
                        .set("next_call_time", LocalDateTime.ofInstant(retryAt, ZoneOffset.UTC))
                        .set("dispatch_token", null)
                        .set("inflight_expire_at", null);
            } else {
                update.set("status", CallDialUnitStatus.FAILED.name());
            }
            callDialUnitMapper.update(null, update);
            return shouldRetry ? RetryDecision.retryAt(retryAt) : RetryDecision.noRetry();
        } finally {
            ShardContextHolder.clear();
        }
    }

    public boolean revertDialingToQueued(
            ShardKey shardKey,
            long taskId,
            long dialUnitId,
            String dispatchToken,
            LocalDateTime nextCallTime
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
            update.eq("task_id", taskId)
                    .eq("id", dialUnitId)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken)
                    .set("status", CallDialUnitStatus.PENDING.name())
                    .set("dispatch_token", null)
                    .set("inflight_expire_at", null)
                    .set("next_call_time", nextCallTime)
                    .set("updated_at", LocalDateTime.now());
            return callDialUnitMapper.update(null, update) > 0;
        } finally {
            ShardContextHolder.clear();
        }
    }

    public boolean revertDialingToReady(
            ShardKey shardKey,
            long taskId,
            long dialUnitId,
            String dispatchToken,
            LocalDateTime updatedAt
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
            update.eq("task_id", taskId)
                    .eq("id", dialUnitId)
                    .eq("status", CallDialUnitStatus.DIALING.name())
                    .eq("dispatch_token", dispatchToken)
                    .set("status", CallDialUnitStatus.READY.name())
                    .set("dispatch_token", null)
                    .set("inflight_expire_at", null)
                    .set("updated_at", updatedAt);
            return callDialUnitMapper.update(null, update) > 0;
        } finally {
            ShardContextHolder.clear();
        }
    }

    public int markRecoveredForRetry(
            ShardKey shardKey,
            long taskId,
            List<Long> ids,
            java.time.Instant retryAt
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId).in("id", ids);
            List<CallDialUnitEntity> units = callDialUnitMapper.selectList(query);
            LocalDateTime nextCallTime = LocalDateTime.ofInstant(retryAt, ZoneOffset.UTC);
            LocalDateTime now = LocalDateTime.now();
            int recovered = 0;
            for (CallDialUnitEntity unit : units) {
                UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
                update.eq("task_id", taskId)
                        .eq("id", unit.getId())
                        .eq("status", CallDialUnitStatus.DIALING.name())
                        .set("retry_count", (unit.getRetryCount() == null ? 0 : unit.getRetryCount()) + 1)
                        .set("updated_at", now);
                int maxRetryCount = unit.getMaxRetryCount() == null ? 0 : unit.getMaxRetryCount();
                int nextRetryCount = (unit.getRetryCount() == null ? 0 : unit.getRetryCount()) + 1;
                if (nextRetryCount <= maxRetryCount) {
                    update.set("status", CallDialUnitStatus.PENDING.name())
                            .set("next_call_time", nextCallTime)
                            .set("dispatch_token", null)
                            .set("inflight_expire_at", null);
                } else {
                    update.set("status", CallDialUnitStatus.FAILED.name());
                }
                if (callDialUnitMapper.update(null, update) > 0) {
                    recovered++;
                }
            }
            return recovered;
        } finally {
            ShardContextHolder.clear();
        }
    }

    private List<ShardKey> allDialShards() {
        List<ShardKey> shards = new ArrayList<>(shardProperties.getDbCount() * shardProperties.getTableCount());
        for (int dbIndex = 0; dbIndex < shardProperties.getDbCount(); dbIndex++) {
            for (int tableIndex = 0; tableIndex < shardProperties.getTableCount(); tableIndex++) {
                shards.add(new ShardKey(0L, dbIndex, tableIndex, "dial"));
            }
        }
        return shards;
    }

    public record DuePendingTask(Long tenantId, Long taskId) {
    }

    public record ExpiredDialingBatch(Long tenantId, Long taskId, List<Long> dialUnitIds) {
    }

    private record ExpiredDialingBatchBuilder(Long tenantId, Long taskId, List<Long> dialUnitIds) {
        private ExpiredDialingBatchBuilder(Long tenantId, Long taskId) {
            this(tenantId, taskId, new ArrayList<>());
        }
    }
}
