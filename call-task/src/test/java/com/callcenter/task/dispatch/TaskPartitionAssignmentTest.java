package com.callcenter.task.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPartitionAssignmentTest {

    @Test
    void shouldAssignTaskToFixedPartition() {
        TaskPartitioner partitioner = new TaskPartitioner(128);

        int first = partitioner.partitionOf(1001L);
        int second = partitioner.partitionOf(1001L);

        assertTrue(first >= 0 && first < 128);
        assertEquals(first, second);
    }
}
