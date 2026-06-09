package com.callcenter.task.dispatch;

import com.callcenter.task.repository.entity.CallDialUnitEntity;
import com.callcenter.task.enums.CallDialUnitStatus;
import com.callcenter.persistence.route.ShardKey;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DialDispatchCompensationService {

    private final CallDialUnitRepository callDialUnitRepository;
    private final RedisDialUnitQueue redisDialUnitQueue;
    private final DispatchConcurrencyLimiter concurrencyLimiter;
    private final CallTaskMetrics metrics;

    public DialDispatchCompensationService(
            CallDialUnitRepository callDialUnitRepository,
            RedisDialUnitQueue redisDialUnitQueue,
            DispatchConcurrencyLimiter concurrencyLimiter,
            CallTaskMetrics metrics
    ) {
        this.callDialUnitRepository = callDialUnitRepository;
        this.redisDialUnitQueue = redisDialUnitQueue;
        this.concurrencyLimiter = concurrencyLimiter;
        this.metrics = metrics;
    }

    public void compensateFailedDispatch(ShardKey shardKey, CallDialUnitEntity unit) {
        boolean reverted = callDialUnitRepository.revertDialingToReady(
                shardKey,
                unit.getTaskId(),
                unit.getId(),
                unit.getDispatchToken(),
                LocalDateTime.now()
        );
        if (!reverted) {
            metrics.incrementDispatchCompensationSkipped();
            return;
        }
        unit.setStatus(CallDialUnitStatus.READY.name());
        unit.setDispatchToken(null);
        unit.setInflightExpireAt(null);
        redisDialUnitQueue.offerReady(unit.getTenantId(), unit.getTaskId(), List.of(unit));
        concurrencyLimiter.release(unit.getTenantId(), unit.getTaskId());
        metrics.incrementDispatchCompensated();
    }
}
