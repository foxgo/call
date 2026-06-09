package com.callcenter.task.repository;

import com.callcenter.task.repository.entity.CallCallerIdEntity;
import com.callcenter.task.repository.mapper.CallCallerIdMapper;
import com.callcenter.task.repository.mapper.CallTaskCallerIdBindingMapper;
import com.callcenter.task.repository.entity.CallTaskCallerIdBindingEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallCallerIdRepositoryTest {

    @Test
    void shouldListActiveCallerIdsByTenant() {
        CallCallerIdMapper mapper = mock(CallCallerIdMapper.class);
        CallCallerIdRepository repository = new CallCallerIdRepository(mapper);
        when(mapper.selectList(any())).thenReturn(List.of(caller(3001L, "02166668888"), caller(3002L, "02166668889")));

        List<CallCallerIdEntity> result = repository.listActiveByTenant(9L);

        assertEquals(List.of("02166668888", "02166668889"), result.stream().map(CallCallerIdEntity::getCallerId).toList());
        verify(mapper, times(1)).selectList(any());
    }

    @Test
    void shouldListBindingsByTask() {
        CallTaskCallerIdBindingMapper mapper = mock(CallTaskCallerIdBindingMapper.class);
        CallTaskCallerIdBindingRepository repository = new CallTaskCallerIdBindingRepository(mapper);
        when(mapper.selectList(any())).thenReturn(List.of(binding(1L, 3001L, "ALLOW"), binding(2L, 3002L, "DENY")));

        List<CallTaskCallerIdBindingEntity> result = repository.listByTask(9L, 1001L);

        assertEquals(2, result.size());
        assertEquals(List.of("ALLOW", "DENY"), result.stream().map(CallTaskCallerIdBindingEntity::getBindingType).toList());
        verify(mapper, times(1)).selectList(any());
    }

    private static CallCallerIdEntity caller(long id, String callerId) {
        CallCallerIdEntity entity = new CallCallerIdEntity();
        entity.setId(id);
        entity.setTenantId(9L);
        entity.setCallerId(callerId);
        entity.setPoolType("SHARED");
        entity.setStatus("ACTIVE");
        return entity;
    }

    private static CallTaskCallerIdBindingEntity binding(long id, long callerIdId, String type) {
        CallTaskCallerIdBindingEntity entity = new CallTaskCallerIdBindingEntity();
        entity.setId(id);
        entity.setTenantId(9L);
        entity.setTaskId(1001L);
        entity.setCallerIdId(callerIdId);
        entity.setBindingType(type);
        return entity;
    }
}
