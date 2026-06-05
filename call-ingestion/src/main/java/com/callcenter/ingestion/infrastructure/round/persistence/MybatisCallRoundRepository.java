package com.callcenter.ingestion.infrastructure.round.persistence;

import com.callcenter.common.context.ShardContextHolder;
import com.callcenter.common.route.ShardKey;
import com.callcenter.ingestion.domain.round.CallRoundMessage;
import com.callcenter.ingestion.support.metrics.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MybatisCallRoundRepository {

    private final CallRoundMapper callRoundMapper;
    private final WriteMetrics writeMetrics;

    public MybatisCallRoundRepository(
            CallRoundMapper callRoundMapper,
            WriteMetrics writeMetrics
    ) {
        this.callRoundMapper = callRoundMapper;
        this.writeMetrics = writeMetrics;
    }

    @Transactional
    public List<CallRoundEntity> persistBatch(ShardKey shardKey, List<CallRoundMessage> batch) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            List<CallRoundEntity> entities = batch.stream().map(this::toEntity).toList();
            Timer.Sample sample = Timer.start();
            callRoundMapper.batchInsertIgnore(entities);
            sample.stop(writeMetrics.mysqlInsertLatency());
            return entities;
        } finally {
            ShardContextHolder.clear();
        }
    }

    public long countByCallId(ShardKey shardKey, long callId) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            return callRoundMapper.countByCallId(callId);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public List<CallRoundEntity> listByCallId(ShardKey shardKey, long callId) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            List<CallRoundEntity> entities = callRoundMapper.selectByCallId(callId);
            return entities == null ? Collections.emptyList() : entities;
        } finally {
            ShardContextHolder.clear();
        }
    }

    private CallRoundEntity toEntity(CallRoundMessage message) {
        if (message.roundId() == null) {
            throw new IllegalArgumentException("roundId is required");
        }
        CallRoundEntity entity = new CallRoundEntity();
        entity.setRoundId(message.roundId());
        entity.setCallId(message.callId());
        entity.setTenantId(message.tenantId());
        entity.setRoundIndex(message.roundIndex());
        entity.setSpeaker(message.speaker());
        entity.setContent(message.content());
        entity.setIntent(message.intent());
        entity.setStartTime(toDateTime(message.startTime()));
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private LocalDateTime toDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofEpochSecond(
                epochMillis / 1000,
                (int) ((epochMillis % 1000) * 1_000_000),
                ZoneOffset.UTC
        );
    }
}
