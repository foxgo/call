package com.callcenter.task.repository;

import com.callcenter.persistence.config.ShardProperties;
import com.callcenter.task.mapper.CallDialUnitMapper;
import com.callcenter.persistence.route.ShardKey;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallDialUnitRepositoryRollbackTest {

    @Test
    void shouldReturnTrueWhenDialingUnitIsRevertedToReady() {
        CallDialUnitMapper mapper = mock(CallDialUnitMapper.class);
        CallDialUnitRepository repository = new CallDialUnitRepository(mapper, shardProperties());
        when(mapper.update(any(), any())).thenReturn(1);

        boolean reverted = repository.revertDialingToReady(
                new ShardKey(9L, 0, 1, "dial"),
                1001L,
                11L,
                "token-1",
                LocalDateTime.of(2026, 6, 2, 12, 0)
        );

        assertTrue(reverted);
        verify(mapper, times(1)).update(any(), any());
    }

    @Test
    void shouldReturnFalseWhenDialingUnitCannotBeRevertedToReady() {
        CallDialUnitMapper mapper = mock(CallDialUnitMapper.class);
        CallDialUnitRepository repository = new CallDialUnitRepository(mapper, shardProperties());
        when(mapper.update(any(), any())).thenReturn(0);

        boolean reverted = repository.revertDialingToReady(
                new ShardKey(9L, 0, 1, "dial"),
                1001L,
                11L,
                "token-1",
                LocalDateTime.of(2026, 6, 2, 12, 0)
        );

        assertFalse(reverted);
        verify(mapper, times(1)).update(any(), any());
    }

    private static ShardProperties shardProperties() {
        ShardProperties properties = new ShardProperties();
        properties.setDbCount(1);
        properties.setTableCount(1);
        return properties;
    }
}
