package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskConcurrencyProperties;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DispatchConcurrencyLimiter {

    private static final Duration KEY_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final CallTaskConcurrencyProperties properties;

    public DispatchConcurrencyLimiter(
            StringRedisTemplate stringRedisTemplate,
            CallTaskConcurrencyProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public boolean tryAcquire(Long tenantId, Long taskId, int taskMaxConcurrency) {
        if (!incrementWithinLimit(globalKey(), properties.getGlobalMax())) {
            return false;
        }
        if (!incrementWithinLimit(tenantKey(tenantId), properties.getTenantDefaultMax())) {
            decrement(globalKey());
            return false;
        }
        if (!incrementWithinLimit(taskKey(taskId), taskMaxConcurrency)) {
            decrement(globalKey());
            decrement(tenantKey(tenantId));
            return false;
        }
        return true;
    }

    public void release(Long tenantId, Long taskId) {
        decrement(globalKey());
        decrement(tenantKey(tenantId));
        decrement(taskKey(taskId));
    }

    private boolean incrementWithinLimit(String key, int max) {
        Long current = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, KEY_TTL);
        if (current == null) {
            return false;
        }
        if (current > max) {
            decrement(key);
            return false;
        }
        return true;
    }

    private void decrement(String key) {
        stringRedisTemplate.opsForValue().decrement(key);
    }

    private String globalKey() {
        return "call:concurrency:global";
    }

    private String tenantKey(Long tenantId) {
        return "call:concurrency:tenant:%d".formatted(tenantId);
    }

    private String taskKey(Long taskId) {
        return "call:concurrency:task:%d".formatted(taskId);
    }
}
