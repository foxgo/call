package com.callcenter.ingestion.repository;

import com.callcenter.persistence.context.ShardContextHolder;
import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.ingestion.service.OutboxEventRepository;
import com.callcenter.ingestion.service.RecordRepository;
import com.callcenter.ingestion.postprocess.OutboxEventFactory;
import com.callcenter.ingestion.model.CallRecordPersistedEvent;
import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRecordMessage;
import com.callcenter.ingestion.support.metrics.WriteMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.function.Consumer;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MybatisCallRecordRepository implements RecordRepository {

    private final CallRecordMapper callRecordMapper;
    private final ShardingRouter shardingRouter;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final WriteMetrics writeMetrics;

    public MybatisCallRecordRepository(
            CallRecordMapper callRecordMapper,
            ShardingRouter shardingRouter,
            OutboxEventRepository outboxEventRepository,
            OutboxEventFactory outboxEventFactory,
            WriteMetrics writeMetrics
    ) {
        this.callRecordMapper = callRecordMapper;
        this.shardingRouter = shardingRouter;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventFactory = outboxEventFactory;
        this.writeMetrics = writeMetrics;
    }

    @Override
    @Transactional
    public CallRecordData save(CallRecordMessage message) {
        ShardKey shardKey = shardingRouter.routeRecord(
                message.tenantId(),
                message.phone(),
                shardingRouter.toDateTime(message.startTime())
        );
        return persistBatch(shardKey, List.of(message)).stream().map(this::toData).findFirst()
                .orElseThrow(() -> new IllegalStateException("record batch save returned no rows"));
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
            List<com.callcenter.ingestion.model.OutboxEventData> outboxEvents = entities.stream()
                    .map(this::toData)
                    .map(CallRecordPersistedEvent::from)
                    .map(outboxEventFactory::recordPersisted)
                    .toList();
            outboxEventRepository.saveAll(outboxEvents);
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

    private CallRecordData toData(CallRecordEntity entity) {
        return new CallRecordData(
                entity.getCallId(),
                entity.getTenantId(),
                entity.getTaskId(),
                entity.getPhone(),
                entity.getLineNumber(),
                entity.getCallStatus(),
                entity.getDuration(),
                entity.getRoundTotal(),
                entity.getRecordingUrl(),
                entity.getErrorCode(),
                entity.getErrorDescription(),
                entity.getHangupBy(),
                entity.getConnected(),
                entity.getRingDuration(),
                entity.getRingStartTime(),
                entity.getHangupTime(),
                entity.getStartTime(),
                entity.getEndTime(),
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
