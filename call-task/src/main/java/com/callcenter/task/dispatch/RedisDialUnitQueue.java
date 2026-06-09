package com.callcenter.task.dispatch;

import com.callcenter.task.repository.entity.CallDialUnitEntity;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.task.config.CallTaskDispatchProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

@Component
public class RedisDialUnitQueue {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisQueueScriptRepository scriptRepository;
    private final ShardingRouter shardingRouter;

    public RedisDialUnitQueue(
            StringRedisTemplate stringRedisTemplate,
            RedisQueueScriptRepository scriptRepository,
            CallTaskDispatchProperties properties,
            ShardingRouter shardingRouter
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.scriptRepository = scriptRepository;
        this.shardingRouter = shardingRouter;
    }

    public List<Long> claimReady(Long tenantId, Long taskId, int batchSize, Instant expireAt) {
        List<String> keys = List.of(RedisQueueKeys.ready(shardingRouter.dbIndex(tenantId), taskId));
        List<?> raw = stringRedisTemplate.execute(
                scriptRepository.claimReadyScript(),
                keys,
                String.valueOf(batchSize)
        );
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(String::valueOf).map(Long::parseLong).toList();
    }

    public void offerReady(Long tenantId, Long taskId, List<CallDialUnitEntity> units) {
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        for (CallDialUnitEntity unit : units) {
            double score = resolveReadyScore(unit);
            tuples.add(new DefaultTypedTuple<>(String.valueOf(unit.getId()), score));
        }
        if (!tuples.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(
                    RedisQueueKeys.ready(shardingRouter.dbIndex(tenantId), taskId),
                    tuples
            );
        }
    }

    public long windowSize(Long tenantId, Long taskId) {
        Long ready = stringRedisTemplate.opsForZSet().zCard(RedisQueueKeys.ready(shardingRouter.dbIndex(tenantId), taskId));
        return zeroIfNull(ready);
    }

    private double resolveReadyScore(CallDialUnitEntity unit) {
        long nextCall = unit.getNextCallTime() == null
                ? 0L
                : unit.getNextCallTime().toInstant(ZoneOffset.UTC).toEpochMilli();
        float score = unit.getScore() == null ? 0F : unit.getScore();
        return nextCall + score;
    }

    private long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}
