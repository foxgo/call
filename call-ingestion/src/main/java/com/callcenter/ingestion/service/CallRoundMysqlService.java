package com.callcenter.ingestion.service;

import com.callcenter.common.context.ShardContextHolder;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.mapper.CallEventOutboxMapper;
import com.callcenter.common.mapper.CallRoundMapper;
import com.callcenter.common.route.ShardKey;
import com.callcenter.ingestion.config.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallRoundMysqlService {

    private final CallRoundMapper callRoundMapper;
    private final CallEventOutboxMapper callEventOutboxMapper;
    private final OutboxEventFactory outboxEventFactory;
    private final WriteMetrics writeMetrics;

    public CallRoundMysqlService(
            CallRoundMapper callRoundMapper,
            CallEventOutboxMapper callEventOutboxMapper,
            OutboxEventFactory outboxEventFactory,
            WriteMetrics writeMetrics
    ) {
        this.callRoundMapper = callRoundMapper;
        this.callEventOutboxMapper = callEventOutboxMapper;
        this.outboxEventFactory = outboxEventFactory;
        this.writeMetrics = writeMetrics;
    }

    @Transactional
    public List<CallRoundEntity> persistBatch(ShardKey shardKey, List<CallRoundMessage> batch) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            List<CallRoundEntity> entities = batch.stream().map(this::toEntity).toList();
            List<CallEventOutboxEntity> outboxEvents = entities.stream()
                    .map(outboxEventFactory::roundPersisted)
                    .toList();
            Timer.Sample sample = Timer.start();
            callRoundMapper.batchInsertIgnore(entities);
            callEventOutboxMapper.batchInsert(outboxEvents);
            sample.stop(writeMetrics.mysqlInsertLatency());
            return entities;
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
