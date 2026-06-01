package com.callcenter.task.dispatch;

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

    public ActiveTaskQueue(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void activate(int partition, Long taskId, long fairScore) {
        stringRedisTemplate.opsForZSet().add(activeKey(partition), String.valueOf(taskId), fairScore);
    }

    public void upsertMeta(Long taskId, Long tenantId, int priority, int weight, int partition, long fairScore) {
        stringRedisTemplate.opsForHash().putAll(metaKey(taskId), Map.of(
                "taskId", String.valueOf(taskId),
                "tenantId", String.valueOf(tenantId),
                "priority", String.valueOf(priority),
                "weight", String.valueOf(weight),
                "partition", String.valueOf(partition),
                "fairScore", String.valueOf(fairScore),
                "state", TaskSchedulingState.ACTIVE.name(),
                "blockedReason", TaskBlockReason.NONE.name()
        ));
        stringRedisTemplate.opsForSet().add(knownTasksKey(), String.valueOf(taskId));
    }

    public Optional<TaskSchedulingMeta> loadMeta(Long taskId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(metaKey(taskId));
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
        String taskId = ids.iterator().next();
        stringRedisTemplate.opsForZSet().remove(activeKey(partition), taskId);
        return Optional.of(Long.parseLong(taskId));
    }

    public Optional<ActiveTaskEntry> pollNextTaskWithMeta(int partition) {
        TypedTuple<String> tuple = stringRedisTemplate.opsForZSet().popMin(activeKey(partition));
        if (tuple == null || tuple.getValue() == null) {
            return Optional.empty();
        }
        Long taskId = Long.parseLong(tuple.getValue());
        return loadMeta(taskId).map(meta -> new ActiveTaskEntry(taskId, meta));
    }

    public void reactivate(Long taskId, long fairScore) {
        updateMeta(taskId, fairScore, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE);
        loadMeta(taskId).ifPresent(meta -> activate(meta.partition(), taskId, fairScore));
    }

    public void reactivate(TaskSchedulingMeta meta, long fairScore) {
        updateMeta(meta.taskId(), fairScore, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE);
        activate(meta.partition(), meta.taskId(), fairScore);
    }

    public void block(Long taskId, TaskBlockReason reason) {
        loadMeta(taskId).ifPresent(meta -> updateMeta(taskId, meta.fairScore(), TaskSchedulingState.BLOCKED, reason));
    }

    public void block(TaskSchedulingMeta meta, TaskBlockReason reason) {
        updateMeta(meta.taskId(), meta.fairScore(), TaskSchedulingState.BLOCKED, reason);
    }

    public void deactivate(Long taskId) {
        loadMeta(taskId).ifPresent(meta -> updateMeta(taskId, meta.fairScore(), TaskSchedulingState.INACTIVE, meta.blockedReason()));
    }

    public List<TaskSchedulingMeta> listKnownMetas() {
        Set<String> taskIds = stringRedisTemplate.opsForSet().members(knownTasksKey());
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        return taskIds.stream()
                .map(Long::parseLong)
                .map(this::loadMeta)
                .flatMap(Optional::stream)
                .toList();
    }

    private String activeKey(int partition) {
        return "call:scheduler:partition:%d:active".formatted(partition);
    }

    private String metaKey(Long taskId) {
        return "call:scheduler:task:%d:meta".formatted(taskId);
    }

    private String knownTasksKey() {
        return "call:scheduler:tasks:known";
    }

    private void updateMeta(Long taskId, long fairScore, TaskSchedulingState state, TaskBlockReason blockedReason) {
        stringRedisTemplate.opsForHash().put(metaKey(taskId), "fairScore", String.valueOf(fairScore));
        stringRedisTemplate.opsForHash().put(metaKey(taskId), "state", state.name());
        stringRedisTemplate.opsForHash().put(metaKey(taskId), "blockedReason", blockedReason.name());
    }

    record ActiveTaskEntry(Long taskId, TaskSchedulingMeta meta) {
    }
}
