package com.callcenter.task.dispatch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallTaskDispatcher {

    private final TaskPartitionManager taskPartitionManager;
    private final PartitionSchedulerWorker partitionSchedulerWorker;

    public CallTaskDispatcher(
            TaskPartitionManager taskPartitionManager,
            PartitionSchedulerWorker partitionSchedulerWorker
    ) {
        this.taskPartitionManager = taskPartitionManager;
        this.partitionSchedulerWorker = partitionSchedulerWorker;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.poll-interval:PT1S}")
    public void dispatchOwnedPartitions() {
        for (int partition : taskPartitionManager.ownedPartitions()) {
            partitionSchedulerWorker.runPartition(partition);
        }
    }
}
