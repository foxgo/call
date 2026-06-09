package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskConcurrencyProperties;
import com.callcenter.task.config.CallTaskCapacityControlProperties;
import com.callcenter.task.dispatch.capacity.TaskTargetConcurrencyRegistry;
import com.callcenter.task.metrics.CallTaskMetrics;
import com.callcenter.persistence.route.ShardingRouter;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
/**
 * 调度并发限流器。
 * 作用不是决定“应该给任务多少目标并发”，而是把目标并发和多级上限一起折算成本轮可领取的额度。
 */
public class DispatchConcurrencyLimiter {

    private static final Duration KEY_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final CallTaskConcurrencyProperties properties;
    private final CallTaskCapacityControlProperties capacityControlProperties;
    private final TaskTargetConcurrencyRegistry taskTargetConcurrencyRegistry;
    private final CallTaskMetrics callTaskMetrics;
    private final ShardingRouter shardingRouter;
    private final DefaultRedisScript<Long> acquireBatchScript;
    private final DefaultRedisScript<Long> releaseBatchScript;

    public DispatchConcurrencyLimiter(
            StringRedisTemplate stringRedisTemplate,
            CallTaskConcurrencyProperties properties,
            CallTaskCapacityControlProperties capacityControlProperties,
            TaskTargetConcurrencyRegistry taskTargetConcurrencyRegistry,
            CallTaskMetrics callTaskMetrics,
            ShardingRouter shardingRouter
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.capacityControlProperties = capacityControlProperties;
        this.taskTargetConcurrencyRegistry = taskTargetConcurrencyRegistry;
        this.callTaskMetrics = callTaskMetrics;
        this.shardingRouter = shardingRouter;
        this.acquireBatchScript = new DefaultRedisScript<>();
        acquireBatchScript.setScriptText("""
                -- granted = min(
                --   本轮请求数 requested,
                --   全局剩余额度,
                --   容量池剩余额度,
                --   租户剩余额度,
                --   任务静态剩余额度,
                --   任务动态 target 剩余额度
                -- )
                local requested = tonumber(ARGV[1])
                local globalMax = tonumber(ARGV[2])
                local poolTarget = tonumber(ARGV[3])
                local tenantMax = tonumber(ARGV[4])
                local taskMax = tonumber(ARGV[5])
                local taskTarget = tonumber(ARGV[6])
                local ttlMs = tonumber(ARGV[7])
                local currentGlobal = tonumber(redis.call('GET', KEYS[1]) or '0')
                local currentPool = tonumber(redis.call('GET', KEYS[2]) or '0')
                local currentTenant = tonumber(redis.call('GET', KEYS[3]) or '0')
                local currentTask = tonumber(redis.call('GET', KEYS[4]) or '0')
                local availableGlobal = math.max(globalMax - currentGlobal, 0)
                local availablePool = math.max(poolTarget - currentPool, 0)
                local availableTenant = math.max(tenantMax - currentTenant, 0)
                local availableTask = math.max(taskMax - currentTask, 0)
                local availableTaskTarget = math.max(taskTarget - currentTask, 0)
                local granted = math.min(requested, availableGlobal, availablePool, availableTenant, availableTask, availableTaskTarget)
                if granted <= 0 then
                    return 0
                end
                redis.call('INCRBY', KEYS[1], granted)
                redis.call('INCRBY', KEYS[2], granted)
                redis.call('INCRBY', KEYS[3], granted)
                redis.call('INCRBY', KEYS[4], granted)
                redis.call('PEXPIRE', KEYS[1], ttlMs)
                redis.call('PEXPIRE', KEYS[2], ttlMs)
                redis.call('PEXPIRE', KEYS[3], ttlMs)
                redis.call('PEXPIRE', KEYS[4], ttlMs)
                return granted
                """);
        acquireBatchScript.setResultType(Long.class);
        this.releaseBatchScript = new DefaultRedisScript<>();
        releaseBatchScript.setScriptText("""
                local requested = tonumber(ARGV[1])
                local currentGlobal = tonumber(redis.call('GET', KEYS[1]) or '0')
                local currentPool = tonumber(redis.call('GET', KEYS[2]) or '0')
                local currentTenant = tonumber(redis.call('GET', KEYS[3]) or '0')
                local currentTask = tonumber(redis.call('GET', KEYS[4]) or '0')
                local released = math.min(requested, currentGlobal, currentPool, currentTenant, currentTask)
                if released <= 0 then
                    return 0
                end
                redis.call('DECRBY', KEYS[1], released)
                redis.call('DECRBY', KEYS[2], released)
                redis.call('DECRBY', KEYS[3], released)
                redis.call('DECRBY', KEYS[4], released)
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
        int poolTarget = taskTargetConcurrencyRegistry.loadPoolTarget(capacityControlProperties.getPoolKey())
                .orElse(capacityControlProperties.getPoolHardMax());
        int taskTarget = taskTargetConcurrencyRegistry.loadTaskTarget(tenantId, taskId)
                .map(state -> state.targetConcurrency())
                .orElse(taskMaxConcurrency);
        // 这里拿到的 granted 是“本轮最多允许占用的并发额度”，
        // 还不是最终实际下发数；后面还要受 ready 队列、外显号选择、CAS 更新结果影响。
        Long granted = stringRedisTemplate.execute(
                acquireBatchScript,
                List.of(globalKey(), poolBusyKey(), tenantKey(tenantId), taskKey(tenantId, taskId)),
                String.valueOf(requested),
                String.valueOf(properties.getGlobalMax()),
                String.valueOf(poolTarget),
                String.valueOf(properties.getTenantDefaultMax()),
                String.valueOf(taskMaxConcurrency),
                String.valueOf(taskTarget),
                String.valueOf(KEY_TTL.toMillis())
        );
        int grantedValue = granted == null ? 0 : granted.intValue();
        if (grantedValue <= 0) {
            recordRejectReason(tenantId, taskId, taskMaxConcurrency, poolTarget, taskTarget);
        }
        return grantedValue;
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
                List.of(globalKey(), poolBusyKey(), tenantKey(tenantId), taskKey(tenantId, taskId)),
                String.valueOf(count),
                String.valueOf(KEY_TTL.toMillis()),
                String.valueOf(KEY_TTL.toMillis())
        );
    }

    public int available(Long tenantId, Long taskId, int taskMaxConcurrency) {
        String current = stringRedisTemplate.opsForValue().get(taskKey(tenantId, taskId));
        int inFlight = current == null ? 0 : Integer.parseInt(current);
        // 这里只看任务静态上限，不包含 taskTarget/pool/global 等动态约束。
        return Math.max(taskMaxConcurrency - inFlight, 0);
    }

    public int currentTaskInFlight(Long tenantId, Long taskId) {
        String current = stringRedisTemplate.opsForValue().get(taskKey(tenantId, taskId));
        return current == null ? 0 : Integer.parseInt(current);
    }

    private String globalKey() {
        return "call:concurrency:global";
    }

    private String tenantKey(Long tenantId) {
        return "call:concurrency:tenant:%d".formatted(tenantId);
    }

    private String taskKey(Long tenantId, Long taskId) {
        return "call:concurrency:task:%s".formatted(
                RedisQueueKeys.taskRef(shardingRouter.dbIndex(tenantId), taskId)
        );
    }

    private String poolBusyKey() {
        return "call:capacity:pool:%s:busy".formatted(capacityControlProperties.getPoolKey());
    }

    private void recordRejectReason(Long tenantId, Long taskId, int taskMaxConcurrency, int poolTarget, int taskTarget) {
        int currentGlobal = currentValue(globalKey());
        int currentPool = currentValue(poolBusyKey());
        int currentTenant = currentValue(tenantKey(tenantId));
        int currentTask = currentTaskInFlight(tenantId, taskId);
        // 拒绝原因按最外层约束到最内层约束顺序判断，便于指标上快速定位瓶颈层级。
        if (currentGlobal >= properties.getGlobalMax()) {
            callTaskMetrics.incrementCapacityReject("global");
            return;
        }
        if (currentTenant >= properties.getTenantDefaultMax()) {
            callTaskMetrics.incrementCapacityReject("tenant");
            return;
        }
        if (currentPool >= poolTarget) {
            callTaskMetrics.incrementCapacityReject("pool");
            return;
        }
        if (currentTask >= taskMaxConcurrency) {
            callTaskMetrics.incrementCapacityReject("taskStatic");
            return;
        }
        if (currentTask >= taskTarget) {
            callTaskMetrics.incrementCapacityReject("taskTarget");
        }
    }

    private int currentValue(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
