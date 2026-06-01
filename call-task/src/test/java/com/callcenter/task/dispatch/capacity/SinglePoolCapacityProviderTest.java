package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.config.CallTaskCapacityControlProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SinglePoolCapacityProviderTest {

    @Test
    void shouldBuildSnapshotFromConfiguredPoolAndBusyCount() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("call:capacity:pool:ai-default:busy")).thenReturn("250");
        when(valueOperations.get("call:capacity:pool:ai-default:health")).thenReturn("0.90");

        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        properties.setPoolKey("ai-default");
        properties.setPoolHardMax(1000);

        SinglePoolCapacityProvider provider = new SinglePoolCapacityProvider(redisTemplate, properties);
        CapacitySnapshot snapshot = provider.snapshot();

        assertEquals("ai-default", snapshot.poolKey());
        assertEquals(1000, snapshot.total());
        assertEquals(250, snapshot.busy());
        assertEquals(750, snapshot.idle());
        assertEquals(0.25d, snapshot.utilization());
        assertEquals(0.90d, snapshot.healthScore());
        assertTrue(provider.available());
        assertEquals(0.90d, provider.healthScore());
    }

    @Test
    void shouldDegradeWhenHealthIsUnavailable() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("call:capacity:pool:ai-default:busy")).thenReturn("100");
        when(valueOperations.get("call:capacity:pool:ai-default:health")).thenReturn("0.0");

        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        properties.setPoolKey("ai-default");
        properties.setPoolHardMax(500);

        SinglePoolCapacityProvider provider = new SinglePoolCapacityProvider(redisTemplate, properties);
        CapacitySnapshot snapshot = provider.snapshot();

        assertEquals(0.0d, snapshot.healthScore());
        assertFalse(provider.available());
        assertEquals(0.0d, provider.healthScore());
    }
}
