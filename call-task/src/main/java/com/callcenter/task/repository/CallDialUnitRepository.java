package com.callcenter.task.repository;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.callcenter.common.context.ShardContextHolder;
import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.enums.CallDialUnitStatus;
import com.callcenter.common.mapper.CallDialUnitMapper;
import com.callcenter.common.route.ShardKey;
import com.callcenter.task.model.RetryDecision;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class CallDialUnitRepository {

    private final CallDialUnitMapper callDialUnitMapper;

    public CallDialUnitRepository(CallDialUnitMapper callDialUnitMapper) {
        this.callDialUnitMapper = callDialUnitMapper;
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

    public List<CallDialUnitEntity> claimPendingForQueue(ShardKey shardKey, long taskId, int limit) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            QueryWrapper<CallDialUnitEntity> query = new QueryWrapper<>();
            query.eq("task_id", taskId)
                    .eq("status", CallDialUnitStatus.PENDING.name())
                    .orderByAsc("next_call_time")
                    .orderByAsc("id")
                    .last("LIMIT " + limit);
            List<CallDialUnitEntity> pending = callDialUnitMapper.selectList(query);
            return pending.stream()
                    .filter(unit -> markQueued(taskId, unit.getId()))
                    .peek(unit -> unit.setStatus(CallDialUnitStatus.QUEUED.name()))
                    .toList();
        } finally {
            ShardContextHolder.clear();
        }
    }

    public List<CallDialUnitEntity> markDialing(
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
            return units.stream()
                    .filter(unit -> updateDialing(taskId, unit.getId(), dispatchToken, callTime, inflightExpireAt))
                    .peek(unit -> {
                        unit.setStatus(CallDialUnitStatus.DIALING.name());
                        unit.setDispatchToken(dispatchToken);
                        unit.setLastCallTime(callTime);
                        unit.setInflightExpireAt(inflightExpireAt);
                        unit.setUpdatedAt(callTime);
                    })
                    .toList();
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

    public RetryDecision markFailedOrRetry(
            ShardKey shardKey,
            long taskId,
            long dialUnitId,
            String dispatchToken,
            String failureCode,
            String failureReason,
            java.time.Instant retryAt
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
                    .set("updated_at", now);
            if (shouldRetry) {
                update.set("status", CallDialUnitStatus.QUEUED.name())
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
                    .set("status", CallDialUnitStatus.QUEUED.name())
                    .set("dispatch_token", null)
                    .set("inflight_expire_at", null)
                    .set("next_call_time", nextCallTime)
                    .set("updated_at", LocalDateTime.now());
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
                    update.set("status", CallDialUnitStatus.QUEUED.name())
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

    private boolean markQueued(long taskId, long unitId) {
        UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
        update.eq("task_id", taskId)
                .eq("id", unitId)
                .eq("status", CallDialUnitStatus.PENDING.name())
                .set("status", CallDialUnitStatus.QUEUED.name());
        return callDialUnitMapper.update(null, update) > 0;
    }

    private boolean updateDialing(
            long taskId,
            long unitId,
            String dispatchToken,
            LocalDateTime callTime,
            LocalDateTime inflightExpireAt
    ) {
        UpdateWrapper<CallDialUnitEntity> update = new UpdateWrapper<>();
        update.eq("task_id", taskId)
                .eq("id", unitId)
                .eq("status", CallDialUnitStatus.QUEUED.name())
                .set("status", CallDialUnitStatus.DIALING.name())
                .set("dispatch_token", dispatchToken)
                .set("last_call_time", callTime)
                .set("inflight_expire_at", inflightExpireAt)
                .set("updated_at", callTime);
        return callDialUnitMapper.update(null, update) > 0;
    }
}
