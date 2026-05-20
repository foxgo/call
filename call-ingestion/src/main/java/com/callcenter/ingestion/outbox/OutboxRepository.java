package com.callcenter.ingestion.outbox;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.mapper.CallEventOutboxMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final CallEventOutboxMapper mapper;

    public OutboxRepository(CallEventOutboxMapper mapper) {
        this.mapper = mapper;
    }

    public List<CallEventOutboxEntity> findPublishableBatch(LocalDateTime now, int batchSize) {
        QueryWrapper<CallEventOutboxEntity> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper.eq("status", OutboxStatus.NEW.name())
                        .or(retry -> retry.eq("status", OutboxStatus.FAILED.name())
                                .le("next_attempt_at", now)))
                .orderByAsc("created_at")
                .last("LIMIT " + batchSize);
        return mapper.selectList(query);
    }

    public void markPublished(Long id, LocalDateTime publishedAt) {
        UpdateWrapper<CallEventOutboxEntity> update = new UpdateWrapper<>();
        update.eq("id", id)
                .set("status", OutboxStatus.PUBLISHED.name())
                .set("last_error", null)
                .set("updated_at", publishedAt);
        mapper.update(null, update);
    }

    public void markFailed(Long id, int attemptCount, String lastError, LocalDateTime nextAttemptAt) {
        UpdateWrapper<CallEventOutboxEntity> update = new UpdateWrapper<>();
        update.eq("id", id)
                .set("status", OutboxStatus.FAILED.name())
                .set("attempt_count", attemptCount)
                .set("last_error", lastError)
                .set("next_attempt_at", nextAttemptAt)
                .set("updated_at", nextAttemptAt);
        mapper.update(null, update);
    }
}
