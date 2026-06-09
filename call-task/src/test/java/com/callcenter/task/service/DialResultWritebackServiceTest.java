package com.callcenter.task.service;

import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.task.repository.entity.CallDialUnitEntity;
import com.callcenter.task.enums.CallDialUnitStatus;
import com.callcenter.task.caller.CallerIdHealthService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DialResultWritebackServiceTest {

    @Test
    void shouldExposeReadyDialUnitStatus() {
        org.junit.jupiter.api.Assertions.assertEquals(CallDialUnitStatus.READY, CallDialUnitStatus.valueOf("READY"));
    }

    @Test
    void shouldReleaseQuotaWhenDialSucceedsWithoutRedisProcessingCleanup() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallerIdHealthService healthService = mock(CallerIdHealthService.class);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.findDialingByDispatchToken(new ShardKey(9L, 0, 1, "dial"), 1001L, 11L, "token-1")).thenReturn(dialingUnit());
        when(repository.markSuccess(new ShardKey(9L, 0, 1, "dial"), 1001L, 11L, "token-1", 3, 45, "NORMAL")).thenReturn(true);

        DialResultWritebackService service = new DialResultWritebackService(
                repository,
                queue,
                limiter,
                new CallTaskDispatchProperties(),
                shardingRouter,
                metrics,
                activationService,
                healthService
        );

        DialResultCallbackRequest request = new DialResultCallbackRequest();
        request.setTaskId(1001L);
        request.setDialUnitId(11L);
        request.setDispatchToken("token-1");
        request.setResultStatus("SUCCESS");
        request.setRingDurationSeconds(3);
        request.setTalkDurationSeconds(45);
        request.setHangupCode("NORMAL");

        service.handleCallback(9L, request);

        verifyNoInteractions(queue);
        verify(limiter).release(9L, 1001L);
        verify(activationService).activate(9L, 1001L);
        verify(healthService).recordFeedback(any());
    }

    @Test
    void shouldIgnoreStaleSuccessCallbackWithoutReleasingQuota() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallerIdHealthService healthService = mock(CallerIdHealthService.class);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));

        DialResultWritebackService service = new DialResultWritebackService(
                repository,
                queue,
                limiter,
                new CallTaskDispatchProperties(),
                shardingRouter,
                metrics,
                activationService,
                healthService
        );

        DialResultCallbackRequest request = new DialResultCallbackRequest();
        request.setTaskId(1001L);
        request.setDialUnitId(11L);
        request.setDispatchToken("token-old");
        request.setResultStatus("SUCCESS");

        service.handleCallback(9L, request);

        verifyNoInteractions(queue);
        verify(limiter, never()).release(9L, 1001L);
        verify(activationService, never()).activate(9L, 1001L);
    }

    @Test
    void shouldMoveFailedDialBackToPendingWithoutRedisRetryQueue() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallerIdHealthService healthService = mock(CallerIdHealthService.class);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(repository.findDialingByDispatchToken(new ShardKey(9L, 0, 1, "dial"), 1001L, 11L, "token-1")).thenReturn(dialingUnit());
        when(repository.markFailedForRetry(any(), eq(1001L), eq(11L), eq("token-1"), any(), any(), any(), eq(4), eq(0), eq("USER_BUSY")))
                .thenReturn(RetryDecision.retryAt(Instant.now().plusSeconds(30)));

        DialResultWritebackService service = new DialResultWritebackService(
                repository,
                queue,
                limiter,
                new CallTaskDispatchProperties(),
                shardingRouter,
                metrics,
                activationService,
                healthService
        );

        DialResultCallbackRequest request = new DialResultCallbackRequest();
        request.setTaskId(1001L);
        request.setDialUnitId(11L);
        request.setDispatchToken("token-1");
        request.setResultStatus("FAILED");
        request.setRingDurationSeconds(4);
        request.setTalkDurationSeconds(0);
        request.setHangupCode("USER_BUSY");

        service.handleCallback(9L, request);

        verifyNoInteractions(queue);
        verify(limiter).release(9L, 1001L);
        verify(activationService).activate(9L, 1001L);
        verify(healthService).recordFeedback(any());
    }

    @Test
    void shouldIgnoreStaleFailureCallbackWithoutReleasingQuota() {
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        DispatchConcurrencyLimiter limiter = mock(DispatchConcurrencyLimiter.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskMetrics metrics = mock(CallTaskMetrics.class);
        TaskActivationService activationService = mock(TaskActivationService.class);
        CallerIdHealthService healthService = mock(CallerIdHealthService.class);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));

        DialResultWritebackService service = new DialResultWritebackService(
                repository,
                queue,
                limiter,
                new CallTaskDispatchProperties(),
                shardingRouter,
                metrics,
                activationService,
                healthService
        );

        DialResultCallbackRequest request = new DialResultCallbackRequest();
        request.setTaskId(1001L);
        request.setDialUnitId(11L);
        request.setDispatchToken("token-old");
        request.setResultStatus("FAILED");

        service.handleCallback(9L, request);

        verifyNoInteractions(queue);
        verify(limiter, never()).release(9L, 1001L);
        verify(activationService, never()).activate(9L, 1001L);
    }

    private static CallDialUnitEntity dialingUnit() {
        CallDialUnitEntity entity = new CallDialUnitEntity();
        entity.setId(11L);
        entity.setTaskId(1001L);
        entity.setTenantId(9L);
        entity.setSelectedCallerId(3001L);
        entity.setAttemptStage("FIRST_ATTEMPT");
        entity.setRetryCount(0);
        return entity;
    }
}
