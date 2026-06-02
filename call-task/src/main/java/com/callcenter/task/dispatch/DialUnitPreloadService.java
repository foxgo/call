package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DialUnitPreloadService {

    private final RedisDialUnitQueue redisDialUnitQueue;
    private final CallDialUnitRepository callDialUnitRepository;
    private final CallTaskDispatchProperties properties;
    private final ShardingRouter shardingRouter;

    public DialUnitPreloadService(
            RedisDialUnitQueue redisDialUnitQueue,
            CallDialUnitRepository callDialUnitRepository,
            CallTaskDispatchProperties properties,
            ShardingRouter shardingRouter
    ) {
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.callDialUnitRepository = callDialUnitRepository;
        this.properties = properties;
        this.shardingRouter = shardingRouter;
    }

    public void preloadRunningTask(CallTaskEntity task) {
        ShardKey shardKey = shardingRouter.routeDialUnit(task.getTenantId(), task.getId());
        long windowSize = redisDialUnitQueue.windowSize(task.getTenantId(), task.getId());
        if (windowSize >= properties.getPreloadThreshold()) {
            return;
        }
        List<com.callcenter.common.entity.CallDialUnitEntity> units = callDialUnitRepository.claimPendingToReady(
                shardKey,
                task.getId(),
                properties.getPreloadBatchSize(),
                LocalDateTime.now()
        );
        if (!units.isEmpty()) {
            redisDialUnitQueue.offerReady(task.getTenantId(), task.getId(), units);
        }
    }
}
