package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.config.CallTaskCapacityControlProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTargetAllocatorTest {

    @Test
    void shouldPreferHigherWeightTasksWhileRespectingPoolLimit() {
        CallTaskCapacityControlProperties properties = new CallTaskCapacityControlProperties();
        properties.setTaskBaseShare(1);
        properties.setTaskMinTarget(1);
        TaskTargetAllocator allocator = new TaskTargetAllocator(properties);

        Map<Long, Integer> allocations = allocator.allocate(10, List.of(
                new TaskTargetAllocationCandidate(1001L, 16, 8, 20),
                new TaskTargetAllocationCandidate(1002L, 4, 4, 10)
        ));

        assertEquals(10, allocations.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(allocations.get(1001L) > allocations.get(1002L));
        assertTrue(allocations.get(1001L) <= 8);
        assertTrue(allocations.get(1002L) <= 4);
    }
}
