package com.callcenter.task.repository;

import com.callcenter.common.entity.CallCallerIdStatsEntity;
import com.callcenter.common.mapper.CallCallerIdStatsMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallCallerIdStatsRepositoryTest {

    @Test
    void shouldFindStatsBucket() {
        CallCallerIdStatsMapper mapper = mock(CallCallerIdStatsMapper.class);
        CallCallerIdStatsRepository repository = new CallCallerIdStatsRepository(mapper);
        LocalDateTime bucket = LocalDateTime.of(2026, 6, 2, 9, 0);
        when(mapper.selectOne(any())).thenReturn(stats(1L, 3001L, "FIRST_ATTEMPT", bucket, 3L));

        var result = repository.findBucket(9L, 3001L, "FIRST_ATTEMPT", bucket);

        assertTrue(result.isPresent());
        assertEquals(3L, result.orElseThrow().getAttemptCount());
        verify(mapper, times(1)).selectOne(any());
    }

    @Test
    void shouldReturnLatestStatsPerCallerId() {
        CallCallerIdStatsMapper mapper = mock(CallCallerIdStatsMapper.class);
        CallCallerIdStatsRepository repository = new CallCallerIdStatsRepository(mapper);
        when(mapper.selectList(any())).thenReturn(List.of(
                stats(11L, 3001L, "FIRST_ATTEMPT", LocalDateTime.of(2026, 6, 2, 10, 0), 5L),
                stats(12L, 3001L, "FIRST_ATTEMPT", LocalDateTime.of(2026, 6, 2, 9, 0), 4L),
                stats(13L, 3002L, "FIRST_ATTEMPT", LocalDateTime.of(2026, 6, 2, 8, 0), 2L)
        ));

        Map<Long, CallCallerIdStatsEntity> result = repository.findLatestByCallerIds(9L, List.of(3001L, 3002L), "FIRST_ATTEMPT");

        assertEquals(2, result.size());
        assertEquals(5L, result.get(3001L).getAttemptCount());
        assertEquals(2L, result.get(3002L).getAttemptCount());
        verify(mapper, times(1)).selectList(any());
    }

    @Test
    void shouldUpsertStatsEntity() {
        CallCallerIdStatsMapper mapper = mock(CallCallerIdStatsMapper.class);
        CallCallerIdStatsRepository repository = new CallCallerIdStatsRepository(mapper);
        CallCallerIdStatsEntity entity = stats(21L, 3001L, "RETRY_ATTEMPT", LocalDateTime.of(2026, 6, 2, 11, 0), 1L);

        repository.upsert(entity);

        verify(mapper, times(1)).upsert(entity);
    }

    private static CallCallerIdStatsEntity stats(
            long id,
            long callerIdId,
            String attemptStage,
            LocalDateTime bucket,
            long attempts
    ) {
        CallCallerIdStatsEntity entity = new CallCallerIdStatsEntity();
        entity.setId(id);
        entity.setTenantId(9L);
        entity.setCallerIdId(callerIdId);
        entity.setAttemptStage(attemptStage);
        entity.setTimeBucket(bucket);
        entity.setAttemptCount(attempts);
        return entity;
    }
}
