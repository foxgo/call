package com.callcenter.task.dispatch;

import com.callcenter.common.route.ShardingRouter;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

@Component
public class ActiveTaskQueue {

    private final StringRedisTemplate stringRedisTemplate;
    private final ShardingRouter shardingRouter;

    public ActiveTaskQueue(StringRedisTemplate stringRedisTemplate, ShardingRouter shardingRouter) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shardingRouter = shardingRouter;
    }

    public void activate(int partition, Long tenantId, Long taskId, long fairScore) {
        stringRedisTemplate.opsForZSet().add(activeKey(partition), taskRef(tenantId, taskId), fairScore);
    }

    public void upsertMeta(Long taskId, Long tenantId, int priority, int weight, int partition, long fairScore) {
        String taskRef = taskRef(tenantId, taskId);
        stringRedisTemplate.opsForHash().putAll(metaKey(taskRef), Map.of(
                "taskId", String.valueOf(taskId),
                "tenantId", String.valueOf(tenantId),
                "priority", String.valueOf(priority),
                "weight", String.valueOf(weight),
                "partition", String.valueOf(partition),
                "fairScore", String.valueOf(fairScore),
                "state", TaskSchedulingState.ACTIVE.name(),
                "blockedReason", TaskBlockReason.NONE.name()
        ));
        stringRedisTemplate.opsForSet().add(knownTasksKey(), taskRef);
    }

    public Optional<TaskSchedulingMeta> loadMeta(Long tenantId, Long taskId) {
        return loadMetaByRef(taskRef(tenantId, taskId));
    }

    private Optional<TaskSchedulingMeta> loadMetaByRef(String taskRef) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(metaKey(taskRef));
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TaskSchedulingMeta(
                Long.parseLong(String.valueOf(entries.get("taskId"))),
                Long.parseLong(String.valueOf(entries.get("tenantId"))),
                Integer.parseInt(String.valueOf(entries.get("priority"))),
                Integer.parseInt(String.valueOf(entries.get("weight"))),
                Integer.parseInt(String.valueOf(entries.get("partition"))),
                Long.parseLong(String.valueOf(entries.getOrDefault("fairScore", "0"))),
                TaskSchedulingState.valueOf(String.valueOf(entries.get("state"))),
                TaskBlockReason.valueOf(String.valueOf(entries.get("blockedReason")))
        ));
    }

    public Optional<Long> pollNextTask(int partition) {
        Set<String> ids = stringRedisTemplate.opsForZSet().range(activeKey(partition), 0, 0);
        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }
        String taskRef = ids.iterator().next();
        stringRedisTemplate.opsForZSet().remove(activeKey(partition), taskRef);
        return Optional.of(RedisQueueKeys.taskId(taskRef));
    }

    public Optional<ActiveTaskEntry> pollNextTaskWithMeta(int partition) {
        TypedTuple<String> tuple = stringRedisTemplate.opsForZSet().popMin(activeKey(partition));
        if (tuple == null || tuple.getValue() == null) {
            return Optional.empty();
        }
        return loadMetaByRef(tuple.getValue()).map(meta -> new ActiveTaskEntry(meta.taskId(), meta));
    }

    public void reactivate(Long tenantId, Long taskId, long fairScore) {
        updateMeta(tenantId, taskId, fairScore, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE);
        loadMeta(tenantId, taskId).ifPresent(meta -> activate(meta.partition(), meta.tenantId(), taskId, fairScore));
    }

    public void reactivate(TaskSchedulingMeta meta, long fairScore) {
        updateMeta(meta.tenantId(), meta.taskId(), fairScore, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE);
        activate(meta.partition(), meta.tenantId(), meta.taskId(), fairScore);
    }

    public void block(Long tenantId, Long taskId, TaskBlockReason reason) {
        loadMeta(tenantId, taskId).ifPresent(meta -> updateMeta(tenantId, taskId, meta.fairScore(), TaskSchedulingState.BLOCKED, reason));
    }

    public void block(TaskSchedulingMeta meta, TaskBlockReason reason) {
        updateMeta(meta.tenantId(), meta.taskId(), meta.fairScore(), TaskSchedulingState.BLOCKED, reason);
    }

    public void deactivate(Long tenantId, Long taskId) {
        loadMeta(tenantId, taskId).ifPresent(meta -> updateMeta(tenantId, taskId, meta.fairScore(), TaskSchedulingState.INACTIVE, meta.blockedReason()));
    }

    public List<TaskSchedulingMeta> listKnownMetas() {
        Set<String> taskIds = stringRedisTemplate.opsForSet().members(knownTasksKey());
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return taskIds.stream()
                .map(this::loadMetaByRef)
                .flatMap(Optional::stream)
                .toList();
    }

    private String activeKey(int partition) {
        return "call:scheduler:partition:%d:active".formatted(partition);
    }

    private String metaKey(String taskRef) {
        return "call:scheduler:task:%s:meta".formatted(taskRef);
    }

    private String knownTasksKey() {
        return "call:scheduler:tasks:known";
    }

    private String taskRef(Long tenantId, Long taskId) {
        return RedisQueueKeys.taskRef(shardingRouter.dbIndex(tenantId), taskId);
    }

    private void updateMeta(Long tenantId, Long taskId, long fairScore, TaskSchedulingState state, TaskBlockReason blockedReason) {
        String taskRef = taskRef(tenantId, taskId);
        stringRedisTemplate.opsForHash().put(metaKey(taskRef), "fairScore", String.valueOf(fairScore));
        stringRedisTemplate.opsForHash().put(metaKey(taskRef), "state", state.name());
        stringRedisTemplate.opsForHash().put(metaKey(taskRef), "blockedReason", blockedReason.name());
    }

    record ActiveTaskEntry(Long taskId, TaskSchedulingMeta meta) {
    }
}
