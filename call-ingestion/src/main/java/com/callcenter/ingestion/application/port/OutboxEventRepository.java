package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.model.OutboxEventData;
import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository {

    void saveAll(List<OutboxEventData> events);

    List<OutboxEventData> claimPublishableBatch(LocalDateTime now, int batchSize, int maxRetries);

    int deleteProcessingById(Long id);

    int recoverStaleProcessingRows(LocalDateTime staleBefore, LocalDateTime recoveredAt);

    void markFailed(
            Long id,
            int attemptCount,
            String lastError,
            LocalDateTime failedAt,
            LocalDateTime nextAttemptAt
    );
}
