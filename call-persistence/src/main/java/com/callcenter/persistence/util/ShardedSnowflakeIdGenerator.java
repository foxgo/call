package com.callcenter.persistence.util;


import com.callcenter.persistence.config.CallIdProperties;
import com.callcenter.persistence.config.ShardProperties;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class ShardedSnowflakeIdGenerator {

    private static final long WORKER_BITS = 10L;
    private static final long SHARD_BITS = 4L;
    private static final long SEQUENCE_BITS = 8L;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long SHARD_SHIFT = SEQUENCE_BITS;
    private static final long WORKER_SHIFT = SHARD_BITS + SEQUENCE_BITS;
    private static final long TIME_SHIFT = WORKER_BITS + SHARD_BITS + SEQUENCE_BITS;

    private final long workerId;
    private final long epoch;
    private final int tableCount;
    private final Clock clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public ShardedSnowflakeIdGenerator(CallIdProperties idProperties, ShardProperties shardProperties) {
        this(idProperties, shardProperties.getTableCount(), Clock.systemUTC());
    }

    ShardedSnowflakeIdGenerator(CallIdProperties idProperties, int tableCount, Clock clock) {
        if (idProperties.getMachineId() > MAX_WORKER_ID) {
            throw new IllegalArgumentException("machineId exceeds max worker range");
        }
        this.workerId = idProperties.getMachineId();
        this.epoch = idProperties.getEpoch();
        this.tableCount = tableCount;
        this.clock = clock;
    }

    public synchronized long nextId(String phone) {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        long shard = Math.floorMod((phone == null ? "" : phone).hashCode(), tableCount) & 0x0F;
        return ((timestamp - epoch) << TIME_SHIFT)
                | (workerId << WORKER_SHIFT)
                | (shard << SHARD_SHIFT)
                | sequence;
    }

    private long currentTime() {
        return clock.millis();
    }

    private long waitUntil(long targetTimestamp) {
        long now = currentTime();
        while (now < targetTimestamp) {
            Thread.onSpinWait();
            now = currentTime();
        }
        return now;
    }
}
