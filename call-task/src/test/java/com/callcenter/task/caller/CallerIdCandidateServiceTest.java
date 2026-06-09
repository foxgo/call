package com.callcenter.task.caller;

import com.callcenter.task.repository.entity.CallCallerIdEntity;
import com.callcenter.task.repository.entity.CallTaskCallerIdBindingEntity;
import com.callcenter.task.repository.CallCallerIdRepository;
import com.callcenter.task.repository.CallTaskCallerIdBindingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallerIdCandidateServiceTest {

    @Test
    void shouldReturnHybridCandidatesFromSharedAndAllowListMinusDenyAndCooldown() {
        CallCallerIdRepository callerRepository = mock(CallCallerIdRepository.class);
        CallTaskCallerIdBindingRepository bindingRepository = mock(CallTaskCallerIdBindingRepository.class);
        CallerIdCandidateService service = new CallerIdCandidateService(callerRepository, bindingRepository);
        LocalDateTime now = LocalDateTime.of(2026, 6, 2, 10, 0);

        when(bindingRepository.listByTask(9L, 1001L)).thenReturn(List.of(
                binding(1L, 3002L, "ALLOW", 20),
                binding(2L, 3003L, "DENY", 0)
        ));
        when(callerRepository.listActiveByTenantAndPoolType(9L, "SHARED")).thenReturn(List.of(
                caller(3001L, "02166668888", "SHARED", null, "ACTIVE"),
                caller(3003L, "02199990000", "SHARED", null, "ACTIVE"),
                caller(3004L, "02177770000", "SHARED", now.plusMinutes(5), "ACTIVE")
        ));
        when(callerRepository.listByIds(9L, List.of(3002L))).thenReturn(List.of(
                caller(3002L, "02112345678", "TASK_DEDICATED", null, "ACTIVE")
        ));

        List<CallerIdCandidate> candidates = service.listCandidates(
                9L,
                1001L,
                new TaskCallerIdPolicy("HYBRID", "ANSWER", 1D, 0D, 0D, 0D, false, 3600, 200),
                now
        );

        assertEquals(List.of("02166668888", "02112345678"), candidates.stream().map(CallerIdCandidate::callerId).toList());
        assertEquals(List.of(0, 20), candidates.stream().map(CallerIdCandidate::priorityBoost).toList());
    }

    @Test
    void shouldRestrictTaskOnlyModeToAllowListedCallers() {
        CallCallerIdRepository callerRepository = mock(CallCallerIdRepository.class);
        CallTaskCallerIdBindingRepository bindingRepository = mock(CallTaskCallerIdBindingRepository.class);
        CallerIdCandidateService service = new CallerIdCandidateService(callerRepository, bindingRepository);
        LocalDateTime now = LocalDateTime.of(2026, 6, 2, 10, 0);

        when(bindingRepository.listByTask(9L, 1001L)).thenReturn(List.of(binding(1L, 3002L, "ALLOW", 5)));
        when(callerRepository.listActiveByTenantAndPoolType(9L, "SHARED")).thenReturn(List.of(
                caller(3001L, "02166668888", "SHARED", null, "ACTIVE")
        ));
        when(callerRepository.listByIds(9L, List.of(3002L))).thenReturn(List.of(
                caller(3002L, "02112345678", "TASK_DEDICATED", null, "ACTIVE")
        ));

        List<CallerIdCandidate> candidates = service.listCandidates(
                9L,
                1001L,
                new TaskCallerIdPolicy("TASK_ONLY", "ANSWER", 1D, 0D, 0D, 0D, false, 3600, 200),
                now
        );

        assertEquals(List.of("02112345678"), candidates.stream().map(CallerIdCandidate::callerId).toList());
    }

    private static CallTaskCallerIdBindingEntity binding(long id, long callerIdId, String bindingType, int priorityBoost) {
        CallTaskCallerIdBindingEntity entity = new CallTaskCallerIdBindingEntity();
        entity.setId(id);
        entity.setTenantId(9L);
        entity.setTaskId(1001L);
        entity.setCallerIdId(callerIdId);
        entity.setBindingType(bindingType);
        entity.setPriorityBoost(priorityBoost);
        return entity;
    }

    private static CallCallerIdEntity caller(
            long id,
            String callerId,
            String poolType,
            LocalDateTime cooldownUntil,
            String status
    ) {
        CallCallerIdEntity entity = new CallCallerIdEntity();
        entity.setId(id);
        entity.setTenantId(9L);
        entity.setCallerId(callerId);
        entity.setPoolType(poolType);
        entity.setCooldownUntil(cooldownUntil);
        entity.setStatus(status);
        return entity;
    }
}
