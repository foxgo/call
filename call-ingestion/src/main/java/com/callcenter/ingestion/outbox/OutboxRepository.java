package com.callcenter.ingestion.outbox;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.mapper.CallEventOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxRepository {

    private final CallEventOutboxMapper mapper;

    public OutboxRepository(CallEventOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public List<CallEventOutboxEntity> claimPublishableBatch(LocalDateTime now, int batchSize) {
        List<Long> ids = mapper.selectPublishableIdsForClaim(now, batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }

        UpdateWrapper<CallEventOutboxEntity> update = new UpdateWrapper<>();
        update.in("id", ids)
                .and(wrapper -> wrapper.eq("status", OutboxStatus.NEW.name())
                        .or(retry -> retry.eq("status", OutboxStatus.FAILED.name())
                                .le("next_attempt_at", now)))
                .set("status", OutboxStatus.PROCESSING.name())
                .set("updated_at", now);
        int claimedCount = mapper.update(null, update);
        if (claimedCount == 0) {
            return List.of();
        }
        return mapper.selectClaimedBatchByIds(ids);
    }

    public int deleteProcessingById(Long id) {
        QueryWrapper<CallEventOutboxEntity> delete = new QueryWrapper<>();
        delete.eq("id", id)
                .eq("status", OutboxStatus.PROCESSING.name());
        return mapper.delete(delete);
    }

    public int recoverStaleProcessingRows(LocalDateTime staleBefore, LocalDateTime recoveredAt) {
        UpdateWrapper<CallEventOutboxEntity> update = new UpdateWrapper<>();
        update.eq("status", OutboxStatus.PROCESSING.name())
                .le("updated_at", staleBefore)
                .set("status", OutboxStatus.FAILED.name())
                .set("next_attempt_at", recoveredAt)
                .set("updated_at", recoveredAt);
        return mapper.update(null, update);
    }

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
}
