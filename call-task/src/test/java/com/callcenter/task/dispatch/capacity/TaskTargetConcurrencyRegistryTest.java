package com.callcenter.task.dispatch.capacity;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskTargetConcurrencyRegistryTest {

    @Test
    void shouldSaveAndLoadPoolTarget() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("call:capacity:pool:ai-default:target")).thenReturn("320");

        TaskTargetConcurrencyRegistry registry = new TaskTargetConcurrencyRegistry(redisTemplate);

        registry.savePoolTarget("ai-default", 320);

        verify(valueOperations).set("call:capacity:pool:ai-default:target", "320");
        assertEquals(Optional.of(320), registry.loadPoolTarget("ai-default"));
    }

    @Test
    void shouldSaveAndLoadTaskTargetState() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Instant updatedAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant cooldownUntil = Instant.parse("2026-06-01T00:00:30Z");
        when(hashOperations.entries("call:capacity:task:1001:control-meta")).thenReturn(Map.of(
                "targetConcurrency", "24",
                "updatedAt", updatedAt.toString(),
                "reason", "pool_expand",
                "cooldownUntil", cooldownUntil.toString()
        ));

        TaskTargetConcurrencyRegistry registry = new TaskTargetConcurrencyRegistry(redisTemplate);
        TaskTargetState state = new TaskTargetState(24, updatedAt, "pool_expand", cooldownUntil);

        registry.saveTaskTarget(1001L, state);

        verify(hashOperations).putAll("call:capacity:task:1001:control-meta", Map.of(
                "targetConcurrency", "24",
                "updatedAt", updatedAt.toString(),
                "reason", "pool_expand",
                "cooldownUntil", cooldownUntil.toString()
        ));
        assertEquals(Optional.of(state), registry.loadTaskTarget(1001L));
    }

    @Test
    void shouldReturnEmptyWhenTaskTargetMissing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("call:capacity:task:1002:control-meta")).thenReturn(Map.of());

        TaskTargetConcurrencyRegistry registry = new TaskTargetConcurrencyRegistry(redisTemplate);

        assertTrue(registry.loadTaskTarget(1002L).isEmpty());
    }
}
