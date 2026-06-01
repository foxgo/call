package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.config.CallTaskCapacityControlProperties;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SinglePoolCapacityProvider implements CapacityProvider {

    private final StringRedisTemplate stringRedisTemplate;
    private final CallTaskCapacityControlProperties properties;

    public SinglePoolCapacityProvider(
            StringRedisTemplate stringRedisTemplate,
            CallTaskCapacityControlProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public CapacitySnapshot snapshot() {
        int total = properties.getPoolHardMax();
        int busy = readInt(poolBusyKey(), 0);
        int clampedBusy = Math.max(0, Math.min(busy, total));
        double healthScore = readDouble(poolHealthKey(), 1.0d);
        int idle = Math.max(total - clampedBusy, 0);
        double utilization = total <= 0 ? 0.0d : ((double) clampedBusy) / total;
        return new CapacitySnapshot(
                properties.getPoolKey(),
                total,
                clampedBusy,
                idle,
                utilization,
                healthScore,
                Instant.now()
        );
    }

    @Override
    public boolean available() {
        return healthScore() > 0.0d && properties.getPoolHardMax() > 0;
    }

    @Override
    public double healthScore() {
        return snapshot().healthScore();
    }

    private int readInt(String key, int defaultValue) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private double readDouble(String key, double defaultValue) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }

    private String poolBusyKey() {
        return "call:capacity:pool:%s:busy".formatted(properties.getPoolKey());
    }

    private String poolHealthKey() {
        return "call:capacity:pool:%s:health".formatted(properties.getPoolKey());
    }
}
