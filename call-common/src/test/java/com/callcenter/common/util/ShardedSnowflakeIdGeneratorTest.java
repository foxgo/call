package com.callcenter.common.util;

import com.callcenter.common.config.CallIdProperties;
import com.callcenter.common.config.ShardProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardedSnowflakeIdGeneratorTest {

    @Test
    void shouldEmbedShardBitsInGeneratedId() {
        CallIdProperties idProperties = new CallIdProperties();
        idProperties.setMachineId(7L);
        ShardProperties shardProperties = new ShardProperties();
        shardProperties.setTableCount(16);

        ShardedSnowflakeIdGenerator generator = new ShardedSnowflakeIdGenerator(idProperties, shardProperties);
        long id = generator.nextId("13800138000");

        int extractedShard = (int) ((id >> 8) & 0x0F);
        assertTrue(extractedShard >= 0);
        assertTrue(extractedShard < 16);
        assertEquals(extractedShard, Math.floorMod("13800138000".hashCode(), 16));
    }
}

