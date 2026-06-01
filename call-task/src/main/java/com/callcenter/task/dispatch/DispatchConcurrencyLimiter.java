package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskConcurrencyProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class DispatchConcurrencyLimiter {

    private static final Duration KEY_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final CallTaskConcurrencyProperties properties;
    private final DefaultRedisScript<Long> acquireBatchScript;
    private final DefaultRedisScript<Long> releaseBatchScript;

    public DispatchConcurrencyLimiter(
            StringRedisTemplate stringRedisTemplate,
            CallTaskConcurrencyProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.acquireBatchScript = new DefaultRedisScript<>();
        acquireBatchScript.setScriptText("""
                local requested = tonumber(ARGV[1])
                local globalMax = tonumber(ARGV[2])
                local tenantMax = tonumber(ARGV[3])
                local taskMax = tonumber(ARGV[4])
                local ttlMs = tonumber(ARGV[5])
                local currentGlobal = tonumber(redis.call('GET', KEYS[1]) or '0')
                local currentTenant = tonumber(redis.call('GET', KEYS[2]) or '0')
                local currentTask = tonumber(redis.call('GET', KEYS[3]) or '0')
                local availableGlobal = math.max(globalMax - currentGlobal, 0)
                local availableTenant = math.max(tenantMax - currentTenant, 0)
                local availableTask = math.max(taskMax - currentTask, 0)
                local granted = math.min(requested, availableGlobal, availableTenant, availableTask)
                if granted <= 0 then
                    return 0
                end
                redis.call('INCRBY', KEYS[1], granted)
                redis.call('INCRBY', KEYS[2], granted)
                redis.call('INCRBY', KEYS[3], granted)
                redis.call('PEXPIRE', KEYS[1], ttlMs)
                redis.call('PEXPIRE', KEYS[2], ttlMs)
                redis.call('PEXPIRE', KEYS[3], ttlMs)
                return granted
                """);
        acquireBatchScript.setResultType(Long.class);
        this.releaseBatchScript = new DefaultRedisScript<>();
        releaseBatchScript.setScriptText("""
                local requested = tonumber(ARGV[1])
                local currentGlobal = tonumber(redis.call('GET', KEYS[1]) or '0')
                local currentTenant = tonumber(redis.call('GET', KEYS[2]) or '0')
                local currentTask = tonumber(redis.call('GET', KEYS[3]) or '0')
                local released = math.min(requested, currentGlobal, currentTenant, currentTask)
                if released <= 0 then
                    return 0
                end
                redis.call('DECRBY', KEYS[1], released)
                redis.call('DECRBY', KEYS[2], released)
                redis.call('DECRBY', KEYS[3], released)
                return released
                """);
        releaseBatchScript.setResultType(Long.class);
    }

    public boolean tryAcquire(Long tenantId, Long taskId, int taskMaxConcurrency) {
        return tryAcquireBatch(tenantId, taskId, taskMaxConcurrency, 1) == 1;
    }

    public int tryAcquireBatch(Long tenantId, Long taskId, int taskMaxConcurrency, int requested) {
        if (requested <= 0) {
            return 0;
        }
        Long granted = stringRedisTemplate.execute(
                acquireBatchScript,
                List.of(globalKey(), tenantKey(tenantId), taskKey(taskId)),
                String.valueOf(requested),
                String.valueOf(properties.getGlobalMax()),
                String.valueOf(properties.getTenantDefaultMax()),
                String.valueOf(taskMaxConcurrency),
                String.valueOf(KEY_TTL.toMillis())
        );
        return granted == null ? 0 : granted.intValue();
    }

    public void release(Long tenantId, Long taskId) {
        releaseBatch(tenantId, taskId, 1);
    }

    public void releaseBatch(Long tenantId, Long taskId, int count) {
        if (count <= 0) {
            return;
        }
        stringRedisTemplate.execute(
                releaseBatchScript,
                List.of(globalKey(), tenantKey(tenantId), taskKey(taskId)),
                String.valueOf(count),
                String.valueOf(KEY_TTL.toMillis())
        );
    }

    public int available(Long tenantId, Long taskId, int taskMaxConcurrency) {
        String current = stringRedisTemplate.opsForValue().get(taskKey(taskId));
        int inFlight = current == null ? 0 : Integer.parseInt(current);
        return Math.max(taskMaxConcurrency - inFlight, 0);
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
