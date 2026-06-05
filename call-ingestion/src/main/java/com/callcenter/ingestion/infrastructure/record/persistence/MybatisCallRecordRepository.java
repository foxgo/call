package com.callcenter.ingestion.infrastructure.record.persistence;

import com.callcenter.common.context.ShardContextHolder;
import com.callcenter.common.route.ShardKey;
import com.callcenter.ingestion.application.outbox.OutboxEventFactory;
import com.callcenter.ingestion.domain.record.CallRecordMessage;
import com.callcenter.ingestion.infrastructure.outbox.persistence.CallEventOutboxEntity;
import com.callcenter.ingestion.infrastructure.outbox.persistence.CallEventOutboxMapper;
import com.callcenter.ingestion.support.metrics.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.function.Consumer;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MybatisCallRecordRepository {

    private final CallRecordMapper callRecordMapper;
    private final CallEventOutboxMapper callEventOutboxMapper;
    private final OutboxEventFactory outboxEventFactory;
    private final WriteMetrics writeMetrics;

    public MybatisCallRecordRepository(
            CallRecordMapper callRecordMapper,
            CallEventOutboxMapper callEventOutboxMapper,
            OutboxEventFactory outboxEventFactory,
            WriteMetrics writeMetrics
    ) {
        this.callRecordMapper = callRecordMapper;
        this.callEventOutboxMapper = callEventOutboxMapper;
        this.outboxEventFactory = outboxEventFactory;
        this.writeMetrics = writeMetrics;
    }

    @Transactional
    public List<CallRecordEntity> persistBatch(ShardKey shardKey, List<CallRecordMessage> batch) {
        return persistBatch(shardKey, batch, entities -> {
        });
    }

    @Transactional
    public List<CallRecordEntity> persistBatch(
            ShardKey shardKey,
            List<CallRecordMessage> batch,
            Consumer<List<CallRecordEntity>> beforeOutboxInsert
    ) {
        ShardContextHolder.set(shardKey.toContext());
        try {
            List<CallRecordEntity> entities = batch.stream().map(this::toEntity).toList();
            Timer.Sample sample = Timer.start();
            callRecordMapper.batchInsertIgnore(entities);
            beforeOutboxInsert.accept(entities);
            ShardContextHolder.set(shardKey.toContext());
            List<CallEventOutboxEntity> outboxEvents = entities.stream()
                    .map(outboxEventFactory::recordPersisted)
                    .toList();
            callEventOutboxMapper.batchInsert(outboxEvents);
            sample.stop(writeMetrics.mysqlInsertLatency());
            return entities;
        } finally {
            ShardContextHolder.clear();
        }
    }

    private CallRecordEntity toEntity(CallRecordMessage message) {
        if (message.callId() == null) {
            throw new IllegalArgumentException("callId is required");
        }
        CallRecordEntity entity = new CallRecordEntity();
        entity.setCallId(message.callId());
        entity.setTenantId(message.tenantId());
        entity.setTaskId(message.taskId());
        entity.setPhone(message.phone());
        entity.setLineNumber(message.lineNumber());
        entity.setCallStatus(message.callStatus());
        entity.setDuration(message.duration());
        entity.setRoundTotal(message.roundTotal());
        entity.setRecordingUrl(message.recordingUrl());
        entity.setErrorCode(message.errorCode());
        entity.setErrorDescription(message.errorDescription());
        entity.setHangupBy(message.hangupBy());
        entity.setConnected(message.connected());
        entity.setRingDuration(message.ringDuration());
        entity.setRingStartTime(toDateTime(message.ringStartTime()));
        entity.setHangupTime(toDateTime(message.hangupTime()));
        entity.setStartTime(toDateTime(message.startTime()));
        entity.setEndTime(toDateTime(message.endTime()));
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
