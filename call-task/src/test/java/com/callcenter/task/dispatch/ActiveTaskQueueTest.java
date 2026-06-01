package com.callcenter.task.dispatch;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveTaskQueueTest {

    @Test
    void shouldPopLowestFairScoreTask() {
        StringRedisTemplate redisTemplate = newStubRedisTemplate();
        ActiveTaskQueue queue = new ActiveTaskQueue(redisTemplate);

        queue.activate(3, 1001L, 10L);
        queue.activate(3, 1002L, 20L);

        assertEquals(Optional.of(1001L), queue.pollNextTask(3));
        assertEquals(Optional.of(1002L), queue.pollNextTask(3));
        assertEquals(Optional.empty(), queue.pollNextTask(3));
    }

    @Test
    void shouldPopLowestFairScoreTaskWithMetaAtomically() {
        StringRedisTemplate redisTemplate = newStubRedisTemplate();
        ActiveTaskQueue queue = new ActiveTaskQueue(redisTemplate);

        queue.upsertMeta(1001L, 9L, 1, 8, 3, 10L);
        queue.upsertMeta(1002L, 9L, 1, 8, 3, 20L);
        queue.activate(3, 1002L, 20L);
        queue.activate(3, 1001L, 10L);

        assertEquals(
                Optional.of(new ActiveTaskQueue.ActiveTaskEntry(
                        1001L,
                        new TaskSchedulingMeta(1001L, 9L, 1, 8, 3, 10L, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE)
                )),
                queue.pollNextTaskWithMeta(3)
        );
        assertEquals(Optional.of(1002L), queue.pollNextTask(3));
    }

    @Test
    void shouldRoundTripSchedulingMeta() {
        StringRedisTemplate redisTemplate = newStubRedisTemplate();
        ActiveTaskQueue queue = new ActiveTaskQueue(redisTemplate);

        queue.upsertMeta(1001L, 9L, 1, 8, 7, 0L);

        assertEquals(
                Optional.of(new TaskSchedulingMeta(1001L, 9L, 1, 8, 7, 0L, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE)),
                queue.loadMeta(1001L)
        );
    }

    @Test
    void shouldReactivateTaskWithUpdatedFairScore() {
        StringRedisTemplate redisTemplate = newStubRedisTemplate();
        ActiveTaskQueue queue = new ActiveTaskQueue(redisTemplate);

        queue.upsertMeta(1001L, 9L, 1, 16, 3, 0L);
        queue.activate(3, 1002L, 20L);

        queue.reactivate(1001L, 80L);

        assertEquals(
                Optional.of(new TaskSchedulingMeta(1001L, 9L, 1, 16, 3, 80L, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE)),
                queue.loadMeta(1001L)
        );
        assertEquals(Optional.of(1002L), queue.pollNextTask(3));
        assertEquals(Optional.of(1001L), queue.pollNextTask(3));
    }

    @Test
    void shouldBlockTaskWithReason() {
        StringRedisTemplate redisTemplate = newStubRedisTemplate();
        ActiveTaskQueue queue = new ActiveTaskQueue(redisTemplate);

        queue.upsertMeta(1001L, 9L, 1, 16, 7, 0L);
        queue.block(1001L, TaskBlockReason.CONCURRENCY_FULL);

        assertEquals(
                Optional.of(new TaskSchedulingMeta(
                        1001L,
                        9L,
                        1,
                        16,
                        7,
                        0L,
                        TaskSchedulingState.BLOCKED,
                        TaskBlockReason.CONCURRENCY_FULL
                )),
                queue.loadMeta(1001L)
        );
    }

    private static StringRedisTemplate newStubRedisTemplate() {
        Map<String, Double> scores = new TreeMap<>();
        Map<String, Map<Object, Object>> hashes = new HashMap<>();
        Set<String> knownTasks = new HashSet<>();

        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = (ZSetOperations<String, String>) Proxy.newProxyInstance(
                ZSetOperations.class.getClassLoader(),
                new Class<?>[]{ZSetOperations.class},
                (proxy, method, args) -> {
                    String key = "call:scheduler:partition:3:active";
                    if ("add".equals(method.getName()) && key.equals(args[0])) {
                        scores.put((String) args[1], (Double) args[2]);
                        return true;
                    }
                    if ("range".equals(method.getName()) && key.equals(args[0])) {
                        return scores.entrySet().stream()
                                .sorted(Map.Entry.<String, Double>comparingByValue()
                                        .thenComparing(Map.Entry.comparingByKey()))
                                .map(Map.Entry::getKey)
                                .limit(((Long) args[2]) - ((Long) args[1]) + 1)
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                    }
                    if ("popMin".equals(method.getName()) && key.equals(args[0])) {
                        Map.Entry<String, Double> entry = scores.entrySet().stream()
                                .sorted(Map.Entry.<String, Double>comparingByValue()
                                        .thenComparing(Map.Entry.comparingByKey()))
                                .findFirst()
                                .orElse(null);
                        if (entry == null) {
                            return null;
                        }
                        scores.remove(entry.getKey());
                        return TypedTuple.of(entry.getKey(), entry.getValue());
                    }
                    if ("remove".equals(method.getName()) && key.equals(args[0])) {
                        long removed = 0L;
                        Object[] members = args.length > 1 && args[1] instanceof Object[] values
                                ? values
                                : java.util.Arrays.copyOfRange(args, 1, args.length);
                        for (Object member : members) {
                            if (scores.remove(String.valueOf(member)) != null) {
                                removed++;
                            }
                        }
                        return removed;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = (HashOperations<String, Object, Object>) Proxy.newProxyInstance(
                HashOperations.class.getClassLoader(),
                new Class<?>[]{HashOperations.class},
                (proxy, method, args) -> {
                    if ("putAll".equals(method.getName())) {
                        hashes.put((String) args[0], new HashMap<>((Map<?, ?>) args[1]));
                        return null;
                    }
                    if ("put".equals(method.getName())) {
                        hashes.computeIfAbsent((String) args[0], ignored -> new HashMap<>()).put(args[1], args[2]);
                        return null;
                    }
                    if ("entries".equals(method.getName())) {
                        return new HashMap<>(hashes.getOrDefault((String) args[0], Map.of()));
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOperations = (SetOperations<String, String>) Proxy.newProxyInstance(
                SetOperations.class.getClassLoader(),
                new Class<?>[]{SetOperations.class},
                (proxy, method, args) -> {
                    if ("add".equals(method.getName()) && "call:scheduler:tasks:known".equals(args[0])) {
                        long added = 0L;
                        Object[] values = (Object[]) args[1];
                        for (Object value : values) {
                            if (knownTasks.add(String.valueOf(value))) {
                                added++;
                            }
                        }
                        return added;
                    }
                    if ("members".equals(method.getName()) && "call:scheduler:tasks:known".equals(args[0])) {
                        return new HashSet<>(knownTasks);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );

        return new StringRedisTemplate() {
            @Override
            public ZSetOperations<String, String> opsForZSet() {
                return zSetOperations;
            }

            @Override
            @SuppressWarnings("unchecked")
            public HashOperations<String, Object, Object> opsForHash() {
                return hashOperations;
            }

            @Override
            @SuppressWarnings("unchecked")
            public SetOperations<String, String> opsForSet() {
                return setOperations;
            }
        };
    }
}
