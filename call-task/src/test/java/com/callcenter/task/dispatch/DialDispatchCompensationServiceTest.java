package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialDispatchCompensationServiceTest {

    @Test
    void shouldRequeueReadyUnitAndReleaseQuotaWhenRollbackSucceeds() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        DialDispatchCompensationService service = new DialDispatchCompensationService(repository, queue, limiter, metrics);
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = dialingUnit();
        when(repository.revertDialingToReady(eq(shardKey), eq(1001L), eq(11L), eq("token-1"), any())).thenReturn(true);

        service.compensateFailedDispatch(shardKey, unit);

        verify(queue).offerReady(eq(9L), eq(1001L), eq(List.of(unit)));
        verify(limiter).release(9L, 1001L);
        verify(metrics).incrementDispatchCompensated();
        verify(metrics, never()).incrementDispatchCompensationSkipped();
    }

    @Test
    void shouldSkipRequeueAndQuotaReleaseWhenRollbackFails() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        DialDispatchCompensationService service = new DialDispatchCompensationService(repository, queue, limiter, metrics);
        ShardKey shardKey = new ShardKey(9L, 0, 1, "dial");
        CallDialUnitEntity unit = dialingUnit();
        when(repository.revertDialingToReady(eq(shardKey), anyLong(), anyLong(), anyString(), any())).thenReturn(false);

        service.compensateFailedDispatch(shardKey, unit);

        verify(queue, never()).offerReady(anyLong(), anyLong(), any());
        verify(limiter, never()).release(anyLong(), anyLong());
        verify(metrics).incrementDispatchCompensationSkipped();
        verify(metrics, never()).incrementDispatchCompensated();
    }

    private static CallDialUnitEntity dialingUnit() {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(11L);
        unit.setTenantId(9L);
        unit.setTaskId(1001L);
        unit.setDispatchToken("token-1");
        unit.setStatus("DIALING");
        unit.setPhone("138001380011");
        return unit;
    }
}
