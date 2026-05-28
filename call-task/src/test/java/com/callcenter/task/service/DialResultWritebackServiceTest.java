package com.callcenter.task.service;

import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.dispatch.DispatchConcurrencyLimiter;
import com.callcenter.task.dispatch.RedisDialUnitQueue;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.model.DialResultCallbackRequest;
import com.callcenter.task.model.RetryDecision;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialResultWritebackServiceTest {

    @Test
    void shouldAckProcessingAndReleaseQuotaWhenDialSucceeds() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.markSuccess(new ShardKey(9L, 0, 1, "dial"), 1001L, 11L, "token-1")).thenReturn(true);

        DialResultWritebackService service = new DialResultWritebackService(
                repository,
                queue,
                limiter,
                new CallTaskDispatchProperties(),
                shardingRouter,
                metrics,
                activationService
        );

        DialResultCallbackRequest request = new DialResultCallbackRequest();
        request.setTaskId(1001L);
        request.setDialUnitId(11L);
        request.setDispatchToken("token-1");
        request.setResultStatus("SUCCESS");

        service.handleCallback(9L, request);

        verify(queue).ackProcessing(9L, 1001L, 1, 11L);
        verify(limiter).release(9L, 1001L);
        verify(activationService).activate(9L, 1001L);
    }

    @Test
    void shouldScheduleRetryWhenDialFailsBeforeRetryLimit() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.markFailedOrRetry(any(), eq(1001L), eq(11L), eq("token-1"), any(), any(), any()))
                .thenReturn(RetryDecision.retryAt(Instant.now().plusSeconds(30)));

        DialResultWritebackService service = new DialResultWritebackService(
                repository,
                queue,
                limiter,
                new CallTaskDispatchProperties(),
                shardingRouter,
                metrics,
                activationService
        );

        DialResultCallbackRequest request = new DialResultCallbackRequest();
        request.setTaskId(1001L);
        request.setDialUnitId(11L);
        request.setDispatchToken("token-1");
        request.setResultStatus("FAILED");

        service.handleCallback(9L, request);

        verify(queue).scheduleRetry(eq(9L), eq(1001L), eq(1), eq(11L), any());
        verify(limiter).release(9L, 1001L);
        verify(activationService).activate(9L, 1001L);
    }

    @Test
    void shouldIgnoreStaleFailureCallbackWithoutReleasingQuota() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.markFailedOrRetry(any(), eq(1001L), eq(11L), eq("token-old"), any(), any(), any()))
                .thenReturn(RetryDecision.stale());

        DialResultWritebackService service = new DialResultWritebackService(
                repository,
                queue,
                limiter,
                new CallTaskDispatchProperties(),
                shardingRouter,
                metrics,
                activationService
        );

        DialResultCallbackRequest request = new DialResultCallbackRequest();
        request.setTaskId(1001L);
        request.setDialUnitId(11L);
        request.setDispatchToken("token-old");
        request.setResultStatus("FAILED");

        service.handleCallback(9L, request);

        verify(queue, never()).ackProcessing(9L, 1001L, 1, 11L);
        verify(limiter, never()).release(9L, 1001L);
        verify(activationService, never()).activate(9L, 1001L);
    }
}
