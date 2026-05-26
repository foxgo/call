package com.callcenter.task.dispatch;

import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisQueueScriptRepository {

    private final DefaultRedisScript<List> claimReadyScript;
    private final DefaultRedisScript<List> requeueDueRetryScript;
    private final DefaultRedisScript<List> recoverExpiredProcessingScript;

    public RedisQueueScriptRepository() {
        this.claimReadyScript = new DefaultRedisScript<>();
        claimReadyScript.setScriptText("""
                local ids = redis.call('ZRANGE', KEYS[1], 0, ARGV[1] - 1)
                for _, id in ipairs(ids) do
                    redis.call('ZREM', KEYS[1], id)
                    redis.call('ZADD', KEYS[2], ARGV[2], id)
                end
                return ids
                """);
        claimReadyScript.setResultType(List.class);

        this.requeueDueRetryScript = new DefaultRedisScript<>();
        requeueDueRetryScript.setScriptText("""
                local ids = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2])
                for _, id in ipairs(ids) do
                    redis.call('ZREM', KEYS[1], id)
                    redis.call('ZADD', KEYS[2], ARGV[3], id)
                end
                return ids
                """);
        requeueDueRetryScript.setResultType(List.class);

        this.recoverExpiredProcessingScript = new DefaultRedisScript<>();
        recoverExpiredProcessingScript.setScriptText("""
                local ids = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2])
                for _, id in ipairs(ids) do
                    redis.call('ZREM', KEYS[1], id)
                    redis.call('ZADD', KEYS[2], ARGV[3], id)
                end
                return ids
                """);
        recoverExpiredProcessingScript.setResultType(List.class);
    }

    public DefaultRedisScript<List> claimReadyScript() {
        return claimReadyScript;
    }

    public DefaultRedisScript<List> requeueDueRetryScript() {
        return requeueDueRetryScript;
    }

    public DefaultRedisScript<List> recoverExpiredProcessingScript() {
        return recoverExpiredProcessingScript;
    }
}
