package com.callcenter.task.dispatch;

import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveTaskQueueTest {

    @Test
    void shouldPopLowestFairScoreTask() {
        StringRedisTemplate redisTemplate = newStubRedisTemplate();
        ActiveTaskQueue queue = new ActiveTaskQueue(redisTemplate);

        queue.activate(3, 1001L, 10L);
        queue.activate(3, 1002L, 20L);

        Optional<Long> taskId = queue.pollNextTask(3);

        assertEquals(Optional.of(1001L), taskId);
    }

    @Test
    void shouldRoundTripSchedulingMeta() {
        StringRedisTemplate redisTemplate = newStubRedisTemplate();
        ActiveTaskQueue queue = new ActiveTaskQueue(redisTemplate);

        queue.upsertMeta(1001L, 9L, 1, 8, 7);

        assertEquals(
                Optional.of(new TaskSchedulingMeta(1001L, 9L, 1, 8, 7, TaskSchedulingState.ACTIVE, TaskBlockReason.NONE)),
                queue.loadMeta(1001L)
        );
    }

    private static StringRedisTemplate newStubRedisTemplate() {
        Map<String, Double> scores = new TreeMap<>();
        Map<String, Map<Object, Object>> hashes = new HashMap<>();

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
                                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
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
                    if ("entries".equals(method.getName())) {
                        return new HashMap<>(hashes.getOrDefault((String) args[0], Map.of()));
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
        };
    }
}
