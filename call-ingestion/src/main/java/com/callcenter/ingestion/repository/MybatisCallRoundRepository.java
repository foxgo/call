package com.callcenter.ingestion.repository;

import com.callcenter.persistence.context.ShardContextHolder;
import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.ingestion.service.RoundRepository;
import com.callcenter.ingestion.model.CallRoundData;
import com.callcenter.ingestion.model.CallRoundMessage;
import com.callcenter.ingestion.support.metrics.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.time.LocalDateTime;
import java.util.List;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MybatisCallRoundRepository implements RoundRepository {

    private final CallRoundMapper callRoundMapper;
    private final ShardingRouter shardingRouter;
    private final WriteMetrics writeMetrics;

    public MybatisCallRoundRepository(
            CallRoundMapper callRoundMapper,
            ShardingRouter shardingRouter,
            WriteMetrics writeMetrics
    ) {
        this.callRoundMapper = callRoundMapper;
        this.shardingRouter = shardingRouter;
        this.writeMetrics = writeMetrics;
    }

    @Override
    @Transactional
    public List<CallRoundData> saveBatch(List<CallRoundMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        CallRoundMessage first = messages.getFirst();
        ShardKey shardKey = shardingRouter.routeRound(
                first.tenantId(),
                first.callId(),
                shardingRouter.toDateTime(first.startTime())
        );
        return persistBatch(shardKey, messages).stream().map(this::toData).toList();
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

    @Override
    public long countByCallId(long tenantId, long callId, LocalDateTime callStartedAt) {
        ShardKey shardKey = shardingRouter.routeRound(tenantId, callId, callStartedAt);
        return countByCallId(shardKey, callId);
    }

    public long countByCallId(ShardKey shardKey, long callId) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            return callRoundMapper.countByCallId(callId);
        } finally {
            ShardContextHolder.clear();
        }
    }

    @Override
    public List<CallRoundData> findByCallId(long tenantId, long callId, LocalDateTime callStartedAt) {
        ShardKey shardKey = shardingRouter.routeRound(tenantId, callId, callStartedAt);
        return listByCallId(shardKey, callId).stream().map(this::toData).toList();
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

    private CallRoundData toData(CallRoundEntity entity) {
        return new CallRoundData(
                entity.getRoundId(),
                entity.getCallId(),
                entity.getTenantId(),
                entity.getRoundIndex(),
                entity.getSpeaker(),
                entity.getContent(),
                entity.getIntent(),
                entity.getStartTime(),
                entity.getCreatedAt()
        );
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
