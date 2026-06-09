package com.callcenter.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.callcenter.task.repository.entity.CallCallerIdStatsEntity;
import com.callcenter.task.repository.mapper.CallCallerIdStatsMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CallCallerIdStatsRepository {

    private final CallCallerIdStatsMapper statsMapper;

    public CallCallerIdStatsRepository(CallCallerIdStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public Optional<CallCallerIdStatsEntity> findBucket(
            Long tenantId,
            Long callerIdId,
            String attemptStage,
            LocalDateTime timeBucket
    ) {
        QueryWrapper<CallCallerIdStatsEntity> query = new QueryWrapper<>();
        query.eq("tenant_id", tenantId)
                .eq("caller_id_id", callerIdId)
                .eq("attempt_stage", attemptStage)
                .eq("time_bucket", timeBucket)
                .last("LIMIT 1");
        return Optional.ofNullable(statsMapper.selectOne(query));
    }

    public Map<Long, CallCallerIdStatsEntity> findLatestByCallerIds(Long tenantId, List<Long> callerIdIds, String attemptStage) {
        if (callerIdIds == null || callerIdIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<CallCallerIdStatsEntity> query = new QueryWrapper<>();
        query.eq("tenant_id", tenantId)
                .eq("attempt_stage", attemptStage)
                .in("caller_id_id", callerIdIds)
                .orderByAsc("caller_id_id")
                .orderByDesc("time_bucket");
        List<CallCallerIdStatsEntity> rows = statsMapper.selectList(query);
        Map<Long, CallCallerIdStatsEntity> latest = new LinkedHashMap<>();
        for (CallCallerIdStatsEntity row : rows) {
            latest.putIfAbsent(row.getCallerIdId(), row);
        }
        return latest;
    }

    public void upsert(CallCallerIdStatsEntity entity) {
        statsMapper.upsert(entity);
    }
}
