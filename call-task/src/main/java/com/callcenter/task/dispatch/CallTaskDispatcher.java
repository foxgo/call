package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallTaskDispatcher {

    private final TaskPartitionManager taskPartitionManager;
    private final PartitionSchedulerWorker partitionSchedulerWorker;
    private final CallTaskDispatchProperties properties;

    public CallTaskDispatcher(
            TaskPartitionManager taskPartitionManager,
            PartitionSchedulerWorker partitionSchedulerWorker,
            CallTaskDispatchProperties properties
    ) {
        this.taskPartitionManager = taskPartitionManager;
        this.partitionSchedulerWorker = partitionSchedulerWorker;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.poll-interval:PT1S}")
    public void dispatchOwnedPartitions() {
        for (int partition : taskPartitionManager.ownedPartitions()) {
            for (int i = 0; i < properties.getMaxTasksPerPartitionTick(); i++) {
                if (!partitionSchedulerWorker.runPartition(partition)) {
                    break;
                }
            }
        }
    }
}
