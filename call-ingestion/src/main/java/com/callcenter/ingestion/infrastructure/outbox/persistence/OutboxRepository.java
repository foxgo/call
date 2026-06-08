package com.callcenter.ingestion.infrastructure.outbox.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.callcenter.ingestion.application.port.OutboxEventRepository;
import com.callcenter.ingestion.domain.model.OutboxEventData;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxRepository implements OutboxEventRepository {

    private final CallEventOutboxMapper mapper;

    public OutboxRepository(CallEventOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void saveAll(List<OutboxEventData> events) {
        if (events.isEmpty()) {
            return;
        }
        mapper.batchInsert(events.stream().map(this::toEntity).toList());
    }

    @Override
    @Transactional
    public List<OutboxEventData> claimPublishableBatch(LocalDateTime now, int batchSize, int maxRetries) {
        List<Long> ids = mapper.selectPublishableIdsForClaim(now, batchSize, maxRetries);
        if (ids.isEmpty()) {
            return List.of();
        }

        UpdateWrapper<CallEventOutboxEntity> update = new UpdateWrapper<>();
        update.in("id", ids)
                .and(wrapper -> wrapper.eq("status", OutboxStatus.NEW.name())
                        .or(retry -> retry.eq("status", OutboxStatus.FAILED.name())
                                .le("next_attempt_at", now)
                                .and(attempt -> attempt.isNull("attempt_count")
                                        .or()
                                        .lt("attempt_count", maxRetries))))
                .set("status", OutboxStatus.PROCESSING.name())
                .set("updated_at", now);
        int claimedCount = mapper.update(null, update);
        if (claimedCount == 0) {
            return List.of();
        }
        return mapper.selectClaimedBatchByIds(ids).stream().map(this::toData).toList();
    }

    @Override
    public int deleteProcessingById(Long id) {
        QueryWrapper<CallEventOutboxEntity> delete = new QueryWrapper<>();
        delete.eq("id", id)
                .eq("status", OutboxStatus.PROCESSING.name());
        return mapper.delete(delete);
    }

    @Override
    public int recoverStaleProcessingRows(LocalDateTime staleBefore, LocalDateTime recoveredAt) {
        UpdateWrapper<CallEventOutboxEntity> update = new UpdateWrapper<>();
        update.eq("status", OutboxStatus.PROCESSING.name())
                .le("updated_at", staleBefore)
                .set("status", OutboxStatus.FAILED.name())
                .set("next_attempt_at", recoveredAt)
                .set("updated_at", recoveredAt);
        return mapper.update(null, update);
    }

    @Override
    public void markFailed(
            Long id,
            int attemptCount,
            String lastError,
            LocalDateTime failedAt,
            LocalDateTime nextAttemptAt
    ) {
        UpdateWrapper<CallEventOutboxEntity> update = new UpdateWrapper<>();
        update.eq("id", id)
                .eq("status", OutboxStatus.PROCESSING.name())
                .set("status", OutboxStatus.FAILED.name())
                .set("attempt_count", attemptCount)
                .set("last_error", lastError)
                .set("next_attempt_at", nextAttemptAt)
                .set("updated_at", failedAt);
        mapper.update(null, update);
    }

    private CallEventOutboxEntity toEntity(OutboxEventData event) {
        CallEventOutboxEntity entity = new CallEventOutboxEntity();
        entity.setId(event.id());
        entity.setEventId(event.eventId());
        entity.setEventType(event.eventType());
        entity.setAggregateType(event.aggregateType());
        entity.setAggregateId(event.aggregateId());
        entity.setTenantId(event.tenantId());
        entity.setPartitionKey(event.partitionKey());
        entity.setSchemaVersion(event.schemaVersion());
        entity.setPayload(event.payload());
        entity.setStatus(event.status());
        entity.setAttemptCount(event.attemptCount());
        entity.setNextAttemptAt(event.nextAttemptAt());
        entity.setLastError(event.lastError());
        entity.setCreatedAt(event.createdAt());
        entity.setUpdatedAt(event.updatedAt());
        return entity;
    }

    private OutboxEventData toData(CallEventOutboxEntity entity) {
        return new OutboxEventData(
                entity.getId(),
                entity.getEventId(),
                entity.getEventType(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getTenantId(),
                entity.getPartitionKey(),
                entity.getSchemaVersion(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getNextAttemptAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
