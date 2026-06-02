package com.callcenter.task.caller;

import com.callcenter.common.entity.CallCallerIdStatsEntity;
import com.callcenter.common.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.repository.CallCallerIdStatsRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallerIdHealthServiceTest {

    @Test
    void shouldCreateNewStatsBucketFromSuccessfulFeedback() {
        CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        when(repository.findBucket(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(idGenerator.nextId(any())).thenReturn(9001L);
        CallerIdHealthService service = new CallerIdHealthService(repository, idGenerator);

        service.recordFeedback(new CallerIdHealthEvent(9L, 3001L, AttemptStage.FIRST_ATTEMPT, true, 5, 45, null));

        verify(repository).upsert(any(CallCallerIdStatsEntity.class));
    }

    @Test
    void shouldMergeIntoExistingBucketAndAppendFailureCode() {
        CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        CallCallerIdStatsEntity existing = new CallCallerIdStatsEntity();
        existing.setId(9001L);
        existing.setTenantId(9L);
        existing.setCallerIdId(3001L);
        existing.setAttemptStage("RETRY_ATTEMPT");
        existing.setTimeBucket(LocalDateTime.of(2026, 6, 2, 10, 0));
        existing.setAttemptCount(2L);
        existing.setRingCount(1L);
        existing.setAnswerCount(1L);
        existing.setSuccessCount(1L);
        existing.setTotalTalkSeconds(30L);
        existing.setFailureCodeSummary("BUSY");
        when(repository.findBucket(any(), any(), any(), any())).thenReturn(Optional.of(existing));
        CallerIdHealthService service = new CallerIdHealthService(repository, idGenerator);

        service.recordFeedback(new CallerIdHealthEvent(9L, 3001L, AttemptStage.RETRY_ATTEMPT, false, 4, 0, "NO_ANSWER"));

        verify(repository).upsert(any(CallCallerIdStatsEntity.class));
        assertEquals("BUSY", existing.getFailureCodeSummary().split(",")[0]);
    }
}
