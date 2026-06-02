package com.callcenter.task.caller;

import com.callcenter.common.entity.CallCallerIdStatsEntity;
import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.task.repository.CallCallerIdStatsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CallerIdSelectorTest {

    @Test
    void shouldSelectBestCallerUsingPreloadedFirstAttemptStats() {
        CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
        CallerIdSelector selector = new CallerIdSelector(repository);

        var result = selector.selectWithStats(
                dialUnit(0),
                new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0D, 0D, 0D, false, 3600, 200),
                List.of(
                        new CallerIdCandidate(3001L, "02166668888", "SHARED", 0D, 1D, 0),
                        new CallerIdCandidate(3002L, "02166668889", "SHARED", 0D, 1D, 0)
                ),
                Map.of(
                        3001L, stats(3001L, "FIRST_ATTEMPT", 10L, 7L, 5L, 300L),
                        3002L, stats(3002L, "FIRST_ATTEMPT", 10L, 3L, 2L, 30L)
                )
        );

        assertTrue(result.isPresent());
        assertEquals("02166668888", result.orElseThrow().callerId());
        assertEquals(AttemptStage.FIRST_ATTEMPT, result.orElseThrow().attemptStage());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldUseRetryAttemptStageWhenSelectingWithPreloadedStats() {
        CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
        CallerIdSelector selector = new CallerIdSelector(repository);

        var result = selector.selectWithStats(
                dialUnit(1),
                new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0.5D, 0D, 0D, false, 3600, 200),
                List.of(
                        new CallerIdCandidate(3001L, "02166668888", "SHARED", 0D, 1D, 0),
                        new CallerIdCandidate(3002L, "02166668889", "SHARED", 0D, 1D, 0)
                ),
                Map.of(
                        3001L, stats(3001L, "RETRY_ATTEMPT", 10L, 2L, 1L, 10L),
                        3002L, stats(3002L, "RETRY_ATTEMPT", 10L, 6L, 4L, 180L)
                )
        );

        assertTrue(result.isPresent());
        assertEquals("02166668889", result.orElseThrow().callerId());
        assertEquals(AttemptStage.RETRY_ATTEMPT, result.orElseThrow().attemptStage());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldUseFirstAttemptStatsForInitialDial() {
        CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
        CallerIdSelector selector = new CallerIdSelector(repository);
        when(repository.findLatestByCallerIds(9L, List.of(3001L, 3002L), "FIRST_ATTEMPT")).thenReturn(Map.of(
                3001L, stats(3001L, "FIRST_ATTEMPT", 10L, 7L, 5L, 300L),
                3002L, stats(3002L, "FIRST_ATTEMPT", 10L, 3L, 2L, 30L)
        ));

        var result = selector.select(
                9L,
                dialUnit(0),
                new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0D, 0D, 0D, false, 3600, 200),
                List.of(
                        new CallerIdCandidate(3001L, "02166668888", "SHARED", 0D, 1D, 0),
                        new CallerIdCandidate(3002L, "02166668889", "SHARED", 0D, 1D, 0)
                )
        );

        assertTrue(result.isPresent());
        assertEquals("02166668888", result.orElseThrow().callerId());
        assertEquals(AttemptStage.FIRST_ATTEMPT, result.orElseThrow().attemptStage());
    }

    @Test
    void shouldUseRetryStatsForRetryDial() {
        CallCallerIdStatsRepository repository = mock(CallCallerIdStatsRepository.class);
        CallerIdSelector selector = new CallerIdSelector(repository);
        when(repository.findLatestByCallerIds(9L, List.of(3001L, 3002L), "RETRY_ATTEMPT")).thenReturn(Map.of(
                3001L, stats(3001L, "RETRY_ATTEMPT", 10L, 2L, 1L, 10L),
                3002L, stats(3002L, "RETRY_ATTEMPT", 10L, 6L, 4L, 180L)
        ));

        var result = selector.select(
                9L,
                dialUnit(1),
                new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0.5D, 0D, 0D, false, 3600, 200),
                List.of(
                        new CallerIdCandidate(3001L, "02166668888", "SHARED", 0D, 1D, 0),
                        new CallerIdCandidate(3002L, "02166668889", "SHARED", 0D, 1D, 0)
                )
        );

        assertTrue(result.isPresent());
        assertEquals("02166668889", result.orElseThrow().callerId());
        assertEquals(AttemptStage.RETRY_ATTEMPT, result.orElseThrow().attemptStage());
    }

    private static CallDialUnitEntity dialUnit(int retryCount) {
        CallDialUnitEntity entity = new CallDialUnitEntity();
        entity.setRetryCount(retryCount);
        return entity;
    }

    private static CallCallerIdStatsEntity stats(
            long callerIdId,
            String attemptStage,
            long attempts,
            long answers,
            long successes,
            long talkSeconds
    ) {
        CallCallerIdStatsEntity entity = new CallCallerIdStatsEntity();
        entity.setCallerIdId(callerIdId);
        entity.setAttemptStage(attemptStage);
        entity.setTimeBucket(LocalDateTime.of(2026, 6, 2, 10, 0));
        entity.setAttemptCount(attempts);
        entity.setAnswerCount(answers);
        entity.setSuccessCount(successes);
        entity.setTotalTalkSeconds(talkSeconds);
        return entity;
    }
}
