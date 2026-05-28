package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.task.config.CallTaskDispatchProperties;
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
    private final TaskPartitioner taskPartitioner;

    public RedisDialUnitQueue(
            StringRedisTemplate stringRedisTemplate,
            RedisQueueScriptRepository scriptRepository,
            CallTaskDispatchProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.scriptRepository = scriptRepository;
        this.taskPartitioner = new TaskPartitioner(properties.getPartitionCount());
    }

    public List<Long> claimReady(Long tenantId, Long taskId, int shard, int batchSize, Instant expireAt) {
        List<String> keys = List.of(
                RedisQueueKeys.ready(taskId, shard),
                RedisQueueKeys.processing(taskId, shard),
                RedisQueueKeys.processingTimeout(taskPartitioner.partitionOf(taskId))
        );
        List<?> raw = stringRedisTemplate.execute(
                scriptRepository.claimReadyScript(),
                keys,
                String.valueOf(batchSize),
                String.valueOf(expireAt.toEpochMilli()),
                String.valueOf(tenantId),
                String.valueOf(taskId),
                String.valueOf(shard)
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

    public void ackProcessing(Long tenantId, Long taskId, int shard, Long dialUnitId) {
        stringRedisTemplate.opsForZSet().remove(RedisQueueKeys.processing(taskId, shard), String.valueOf(dialUnitId));
        stringRedisTemplate.opsForZSet().remove(
                RedisQueueKeys.processingTimeout(taskPartitioner.partitionOf(taskId)),
                RedisQueueKeys.processingTimeoutMember(tenantId, taskId, shard, dialUnitId)
        );
    }

    public void scheduleRetry(Long tenantId, Long taskId, int shard, Long dialUnitId, Instant retryAt) {
        stringRedisTemplate.opsForZSet()
                .add(RedisQueueKeys.retry(taskId, shard), String.valueOf(dialUnitId), retryAt.toEpochMilli());
        stringRedisTemplate.opsForZSet()
                .add(
                        RedisQueueKeys.retryDue(taskPartitioner.partitionOf(taskId)),
                        RedisQueueKeys.retryDueMember(tenantId, taskId, shard, dialUnitId),
                        retryAt.toEpochMilli()
                );
    }

    public void returnReady(Long tenantId, Long taskId, int shard, Long dialUnitId, double readyScore) {
        ackProcessing(tenantId, taskId, shard, dialUnitId);
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

    public List<RetryDueItem> popDueRetryItems(int partition, Instant now, int limit) {
        List<?> raw = stringRedisTemplate.execute(
                scriptRepository.popDueMembersScript(),
                List.of(RedisQueueKeys.retryDue(partition)),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(limit)
        );
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(String::valueOf).map(RetryDueItem::parse).toList();
    }

    public List<ProcessingTimeoutItem> popExpiredProcessingItems(int partition, Instant now, int limit) {
        List<?> raw = stringRedisTemplate.execute(
                scriptRepository.popDueMembersScript(),
                List.of(RedisQueueKeys.processingTimeout(partition)),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(limit)
        );
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(String::valueOf).map(ProcessingTimeoutItem::parse).toList();
    }

    public boolean requeueRetryUnit(Long taskId, int shard, Long dialUnitId, double readyScore) {
        Long removed = stringRedisTemplate.opsForZSet().remove(RedisQueueKeys.retry(taskId, shard), String.valueOf(dialUnitId));
        if (zeroIfNull(removed) == 0L) {
            return false;
        }
        stringRedisTemplate.opsForZSet().add(RedisQueueKeys.ready(taskId, shard), String.valueOf(dialUnitId), readyScore);
        return true;
    }

    public boolean recoverExpiredProcessingUnit(Long taskId, int shard, Long dialUnitId, double readyScore) {
        Long removed = stringRedisTemplate.opsForZSet().remove(RedisQueueKeys.processing(taskId, shard), String.valueOf(dialUnitId));
        if (zeroIfNull(removed) == 0L) {
            return false;
        }
        stringRedisTemplate.opsForZSet().add(RedisQueueKeys.ready(taskId, shard), String.valueOf(dialUnitId), readyScore);
        return true;
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
