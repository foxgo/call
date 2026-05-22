package com.callcenter.ingestion.service;

import com.callcenter.common.context.ShardContextHolder;
import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.mapper.CallEventOutboxMapper;
import com.callcenter.common.mapper.CallRecordMapper;
import com.callcenter.common.route.ShardKey;
import com.callcenter.ingestion.config.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallRecordMysqlService {

    private final CallRecordMapper callRecordMapper;
    private final CallEventOutboxMapper callEventOutboxMapper;
    private final OutboxEventFactory outboxEventFactory;
    private final WriteMetrics writeMetrics;

    public CallRecordMysqlService(
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
