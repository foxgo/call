package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
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

    public RedisDialUnitQueue(
            StringRedisTemplate stringRedisTemplate,
            RedisQueueScriptRepository scriptRepository,
            CallTaskDispatchProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.scriptRepository = scriptRepository;
    }

    public List<Long> claimReady(Long tenantId, Long taskId, int shard, int batchSize, Instant expireAt) {
        List<String> keys = List.of(RedisQueueKeys.ready(taskId, shard));
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

    public void offerReady(Long taskId, int shard, List<CallDialUnitEntity> units) {
        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        for (CallDialUnitEntity unit : units) {
            double score = resolveReadyScore(unit);
            tuples.add(new DefaultTypedTuple<>(String.valueOf(unit.getId()), score));
        }
        if (!tuples.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(RedisQueueKeys.ready(taskId, shard), tuples);
        }
    }

    public long windowSize(Long taskId, int shard) {
        Long ready = stringRedisTemplate.opsForZSet().zCard(RedisQueueKeys.ready(taskId, shard));
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
