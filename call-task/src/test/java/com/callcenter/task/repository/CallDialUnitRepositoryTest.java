package com.callcenter.task.repository;

import com.callcenter.common.config.ShardProperties;
import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.enums.CallDialUnitStatus;
import com.callcenter.common.mapper.CallDialUnitMapper;
import com.callcenter.common.route.ShardKey;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallDialUnitRepositoryTest {

    @Test
    void shouldClaimPendingToReadyWithSingleBatchUpdate() {
        CallDialUnitMapper mapper = mock(CallDialUnitMapper.class);
        CallDialUnitRepository repository = new CallDialUnitRepository(mapper, shardProperties());
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 10, 0);
        List<CallDialUnitEntity> pending = List.of(
                pendingUnit(11L, 1001L, 9L, "138001380011"),
                pendingUnit(12L, 1001L, 9L, "138001380012")
        );
        when(mapper.selectList(any()))
                .thenReturn(pending)
                .thenReturn(List.of(readyUnit(11L), readyUnit(12L)));
        when(mapper.update(any(), any())).thenReturn(2);

        List<CallDialUnitEntity> claimed = repository.claimPendingToReady(shardKey, 1001L, 2, now);

        assertEquals(2, claimed.size());
        assertIterableEquals(List.of(11L, 12L), claimed.stream().map(CallDialUnitEntity::getId).toList());
        assertIterableEquals(
                List.of(CallDialUnitStatus.READY.name(), CallDialUnitStatus.READY.name()),
                claimed.stream().map(CallDialUnitEntity::getStatus).toList()
        );
        verify(mapper, times(2)).selectList(any());
        verify(mapper, times(1)).update(any(), any());
    }

    @Test
    void shouldMarkReadyUnitsDialingWithSingleBatchUpdate() {
        CallDialUnitMapper mapper = mock(CallDialUnitMapper.class);
        CallDialUnitRepository repository = new CallDialUnitRepository(mapper, shardProperties());
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        LocalDateTime callTime = LocalDateTime.of(2026, 5, 29, 10, 0);
        LocalDateTime expireAt = callTime.plusMinutes(5);
        when(mapper.selectList(any()))
                .thenReturn(List.of(
                        readyUnit(11L),
                        readyUnit(12L)
                ))
                .thenReturn(List.of(
                        dialingUnit(11L, "token-1", callTime, expireAt),
                        dialingUnit(12L, "token-1", callTime, expireAt)
                ));
        when(mapper.update(any(), any())).thenReturn(2);

        List<CallDialUnitEntity> units = repository.markDialingFromReady(
                shardKey,
                1001L,
                List.of(11L, 12L),
                "token-1",
                callTime,
                expireAt
        );

        assertEquals(2, units.size());
        assertIterableEquals(List.of(11L, 12L), units.stream().map(CallDialUnitEntity::getId).toList());
        assertIterableEquals(
                List.of(CallDialUnitStatus.DIALING.name(), CallDialUnitStatus.DIALING.name()),
                units.stream().map(CallDialUnitEntity::getStatus).toList()
        );
        assertIterableEquals(List.of("token-1", "token-1"), units.stream().map(CallDialUnitEntity::getDispatchToken).toList());
        verify(mapper, times(2)).selectList(any());
        verify(mapper, times(1)).update(any(), any());
    }

    private static ShardProperties shardProperties() {
        ShardProperties properties = new ShardProperties();
        properties.setDbCount(1);
        properties.setTableCount(1);
        return properties;
    }

    private static CallDialUnitEntity pendingUnit(long id, long taskId, long tenantId, String phone) {
        CallDialUnitEntity entity = new CallDialUnitEntity();
        entity.setId(id);
        entity.setTaskId(taskId);
        entity.setTenantId(tenantId);
        entity.setPhone(phone);
        entity.setStatus(CallDialUnitStatus.PENDING.name());
        return entity;
    }

    private static CallDialUnitEntity readyUnit(long id) {
        CallDialUnitEntity entity = new CallDialUnitEntity();
        entity.setId(id);
        entity.setTaskId(1001L);
        entity.setTenantId(9L);
        entity.setPhone("1380013800" + id);
        entity.setStatus(CallDialUnitStatus.READY.name());
        return entity;
    }

    private static CallDialUnitEntity dialingUnit(long id, String token, LocalDateTime callTime, LocalDateTime expireAt) {
        CallDialUnitEntity entity = readyUnit(id);
        entity.setStatus(CallDialUnitStatus.DIALING.name());
        entity.setDispatchToken(token);
        entity.setLastCallTime(callTime);
        entity.setInflightExpireAt(expireAt);
        entity.setUpdatedAt(callTime);
        return entity;
    }
}
