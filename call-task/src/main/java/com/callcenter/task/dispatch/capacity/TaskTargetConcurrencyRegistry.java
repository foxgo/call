package com.callcenter.task.dispatch.capacity;

import com.callcenter.common.route.ShardingRouter;
import com.callcenter.task.dispatch.RedisQueueKeys;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskTargetConcurrencyRegistry {

    private final StringRedisTemplate stringRedisTemplate;
    private final ShardingRouter shardingRouter;

    public TaskTargetConcurrencyRegistry(StringRedisTemplate stringRedisTemplate, ShardingRouter shardingRouter) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shardingRouter = shardingRouter;
    }

    public void savePoolTarget(String poolKey, int targetConcurrency) {
        stringRedisTemplate.opsForValue().set(poolTargetKey(poolKey), String.valueOf(targetConcurrency));
    }

    public Optional<Integer> loadPoolTarget(String poolKey) {
        String value = stringRedisTemplate.opsForValue().get(poolTargetKey(poolKey));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(value));
    }

    public void saveTaskTarget(Long tenantId, Long taskId, TaskTargetState state) {
        stringRedisTemplate.opsForHash().putAll(taskTargetMetaKey(tenantId, taskId), Map.of(
                "targetConcurrency", String.valueOf(state.targetConcurrency()),
                "updatedAt", state.updatedAt().toString(),
                "reason", state.reason(),
                "cooldownUntil", state.cooldownUntil().toString()
        ));
    }

    public Optional<TaskTargetState> loadTaskTarget(Long tenantId, Long taskId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(taskTargetMetaKey(tenantId, taskId));
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TaskTargetState(
                Integer.parseInt(String.valueOf(entries.get("targetConcurrency"))),
                Instant.parse(String.valueOf(entries.get("updatedAt"))),
                String.valueOf(entries.get("reason")),
                Instant.parse(String.valueOf(entries.get("cooldownUntil")))
        ));
    }

    private String poolTargetKey(String poolKey) {
        return "call:capacity:pool:%s:target".formatted(poolKey);
    }

    private String taskTargetMetaKey(Long tenantId, Long taskId) {
        return "call:capacity:task:%s:control-meta".formatted(
                RedisQueueKeys.taskRef(shardingRouter.dbIndex(tenantId), taskId)
        );
    }
}
