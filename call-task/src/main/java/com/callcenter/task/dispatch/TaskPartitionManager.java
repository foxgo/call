package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskPartitionManager {

    private final StringRedisTemplate stringRedisTemplate;
    private final InstanceIdentityProvider instanceIdentityProvider;
    private final CallTaskDispatchProperties properties;
    private final Set<Integer> ownedPartitions = new ConcurrentSkipListSet<>();
    private final DefaultRedisScript<Long> renewLeaseScript;

    public TaskPartitionManager(
            StringRedisTemplate stringRedisTemplate,
            InstanceIdentityProvider instanceIdentityProvider,
            CallTaskDispatchProperties properties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.instanceIdentityProvider = instanceIdentityProvider;
        this.properties = properties;
        this.renewLeaseScript = new DefaultRedisScript<>();
        renewLeaseScript.setScriptText("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    redis.call('PEXPIRE', KEYS[1], ARGV[2])
                    return 1
                end
                return 0
                """);
        renewLeaseScript.setResultType(Long.class);
    }

    public boolean tryAcquire(int partition) {
        boolean acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                ownerKey(partition),
                instanceIdentityProvider.instanceId(),
                properties.getPartitionLeaseTtl()
        ));
        if (acquired) {
            ownedPartitions.add(partition);
        }
        return acquired;
    }

    public List<Integer> ownedPartitions() {
        return new ArrayList<>(ownedPartitions);
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.partition-acquire-interval:PT5S}")
    public void refreshOwnership() {
        for (int partition = 0; partition < properties.getPartitionCount(); partition++) {
            if (ownedPartitions.contains(partition)) {
                renewOwnership(partition);
                continue;
            }
            tryAcquire(partition);
        }
    }

    private boolean renewOwnership(int partition) {
        Long renewed = stringRedisTemplate.execute(
                renewLeaseScript,
                List.of(ownerKey(partition)),
                instanceIdentityProvider.instanceId(),
                String.valueOf(properties.getPartitionLeaseTtl().toMillis())
        );
        boolean stillOwned = Long.valueOf(1L).equals(renewed);
        if (!stillOwned) {
            ownedPartitions.remove(partition);
        }
        return stillOwned;
    }

    private String ownerKey(int partition) {
        return "call:scheduler:partition:%d:owner".formatted(partition);
    }
}
