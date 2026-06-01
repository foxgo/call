package com.callcenter.task.dispatch.capacity;

import com.callcenter.task.config.CallTaskCapacityControlProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskTargetAllocator {

    private final CallTaskCapacityControlProperties properties;

    public TaskTargetAllocator(CallTaskCapacityControlProperties properties) {
        this.properties = properties;
    }

    public Map<Long, Integer> allocate(int poolTarget, List<TaskTargetAllocationCandidate> candidates) {
        if (poolTarget <= 0 || candidates.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> allocations = new HashMap<>();
        int remaining = poolTarget;
        for (TaskTargetAllocationCandidate candidate : candidates) {
            int baseline = Math.min(
                    Math.min(properties.getTaskBaseShare(), candidate.desiredTarget()),
                    candidate.maxConcurrency()
            );
            allocations.put(candidate.taskId(), baseline);
            remaining -= baseline;
        }

        if (remaining <= 0) {
            return allocations;
        }

        int totalWeight = candidates.stream().mapToInt(TaskTargetAllocationCandidate::weight).sum();
        for (TaskTargetAllocationCandidate candidate : candidates) {
            if (remaining <= 0) {
                break;
            }
            int current = allocations.getOrDefault(candidate.taskId(), 0);
            int headroom = Math.max(Math.min(candidate.desiredTarget(), candidate.maxConcurrency()) - current, 0);
            if (headroom <= 0) {
                continue;
            }
            int weightedShare = totalWeight <= 0 ? 0 : (remaining * candidate.weight()) / totalWeight;
            int extra = Math.min(Math.max(weightedShare, 0), headroom);
            allocations.put(candidate.taskId(), current + extra);
            remaining -= extra;
            totalWeight -= candidate.weight();
        }

        if (remaining > 0) {
            for (TaskTargetAllocationCandidate candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                int current = allocations.getOrDefault(candidate.taskId(), 0);
                int headroom = Math.max(Math.min(candidate.desiredTarget(), candidate.maxConcurrency()) - current, 0);
                if (headroom <= 0) {
                    continue;
                }
                int extra = Math.min(headroom, remaining);
                allocations.put(candidate.taskId(), current + extra);
                remaining -= extra;
            }
        }

        return allocations;
    }
}
