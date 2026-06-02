package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import java.time.LocalDateTime;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialUnitPreloadServiceTest {

    @Test
    void shouldLoadPendingDialUnitsIntoReadyQueueWhenWindowBelowThreshold() {
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setPreloadThreshold(2);
        properties.setPreloadBatchSize(50);

        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(queue.windowSize(9L, 1001L)).thenReturn(0L);
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(11L);
        when(repository.claimPendingToReady(
                eq(new ShardKey(9L, 0, 1, "dial")),
                eq(1001L),
                eq(50),
                any(LocalDateTime.class)
        ))
                .thenReturn(List.of(unit));

        DialUnitPreloadService service = new DialUnitPreloadService(queue, repository, properties, shardingRouter);
        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);

        service.preloadRunningTask(task);

        verify(queue).offerReady(eq(9L), eq(1001L), anyList());
        verify(repository).claimPendingToReady(eq(new ShardKey(9L, 0, 1, "dial")), eq(1001L), eq(50), any(LocalDateTime.class));
    }

    @Test
    void shouldSkipPreloadWhenWindowAlreadyFull() {
        RedisDialUnitQueue queue = mock(RedisDialUnitQueue.class);
        CallDialUnitRepository repository = mock(CallDialUnitRepository.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setPreloadThreshold(2);

        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(queue.windowSize(9L, 1001L)).thenReturn(2L);

        DialUnitPreloadService service = new DialUnitPreloadService(queue, repository, properties, shardingRouter);
        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);

        service.preloadRunningTask(task);

        verify(repository, never()).claimPendingToReady(
                eq(new ShardKey(9L, 0, 1, "dial")),
                eq(1001L),
                eq(properties.getPreloadBatchSize()),
                any(LocalDateTime.class)
        );
    }
}
