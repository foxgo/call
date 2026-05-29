package com.callcenter.task.dispatch;

import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisQueueScriptRepository {

    private final DefaultRedisScript<List> claimReadyScript;

    public RedisQueueScriptRepository() {
        this.claimReadyScript = new DefaultRedisScript<>();
        claimReadyScript.setScriptText("""
                local ids = redis.call('ZRANGE', KEYS[1], 0, ARGV[1] - 1)
                for _, id in ipairs(ids) do
                    redis.call('ZREM', KEYS[1], id)
                end
                return ids
                """);
        claimReadyScript.setResultType(List.class);
    }

    public DefaultRedisScript<List> claimReadyScript() {
        return claimReadyScript;
    }
}
