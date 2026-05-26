package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
            RedisQueueScriptRepository scriptRepository
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.scriptRepository = scriptRepository;
    }

    public List<Long> claimReady(Long taskId, int shard, int batchSize, Instant expireAt) {
        List<String> keys = List.of(
                RedisQueueKeys.ready(taskId, shard),
                RedisQueueKeys.processing(taskId, shard)
        );
        List<?> raw = stringRedisTemplate.execute(
                scriptRepository.claimReadyScript(),
                keys,
                String.valueOf(batchSize),
                String.valueOf(expireAt.toEpochMilli())
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

    public void ackProcessing(Long taskId, int shard, Long dialUnitId) {
        stringRedisTemplate.opsForZSet().remove(RedisQueueKeys.processing(taskId, shard), String.valueOf(dialUnitId));
    }

    public void scheduleRetry(Long taskId, int shard, Long dialUnitId, Instant retryAt) {
        stringRedisTemplate.opsForZSet()
                .add(RedisQueueKeys.retry(taskId, shard), String.valueOf(dialUnitId), retryAt.toEpochMilli());
    }

    public void returnReady(Long taskId, int shard, Long dialUnitId, double readyScore) {
        ackProcessing(taskId, shard, dialUnitId);
        stringRedisTemplate.opsForZSet().add(RedisQueueKeys.ready(taskId, shard), String.valueOf(dialUnitId), readyScore);
    }

    public long windowSize(Long taskId, int shard) {
        Long ready = stringRedisTemplate.opsForZSet().zCard(RedisQueueKeys.ready(taskId, shard));
        Long processing = stringRedisTemplate.opsForZSet().zCard(RedisQueueKeys.processing(taskId, shard));
        Long retry = stringRedisTemplate.opsForZSet().zCard(RedisQueueKeys.retry(taskId, shard));
        return zeroIfNull(ready) + zeroIfNull(processing) + zeroIfNull(retry);
    }

    public List<Long> requeueDueRetry(Long taskId, int shard, Instant now, int limit, double readyScore) {
        List<?> raw = stringRedisTemplate.execute(
                scriptRepository.requeueDueRetryScript(),
                List.of(RedisQueueKeys.retry(taskId, shard), RedisQueueKeys.ready(taskId, shard)),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(limit),
                String.valueOf(readyScore)
        );
        return normalizeIds(raw);
    }

    public List<Long> recoverExpiredProcessing(Long taskId, int shard, Instant now, int limit, double readyScore) {
        List<?> raw = stringRedisTemplate.execute(
                scriptRepository.recoverExpiredProcessingScript(),
                List.of(RedisQueueKeys.processing(taskId, shard), RedisQueueKeys.ready(taskId, shard)),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(limit),
                String.valueOf(readyScore)
        );
        return normalizeIds(raw);
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

    private List<Long> normalizeIds(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(String::valueOf)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
