package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CallTaskDispatcher.class);

    private final TaskPartitionManager taskPartitionManager;
    private final PartitionSchedulerWorker partitionSchedulerWorker;
    private final CallTaskDispatchProperties properties;
    private final Executor dispatchExecutor;
    private final ConcurrentHashMap<Integer, AtomicBoolean> runningPartitions = new ConcurrentHashMap<>();

    public CallTaskDispatcher(
            TaskPartitionManager taskPartitionManager,
            PartitionSchedulerWorker partitionSchedulerWorker,
            CallTaskDispatchProperties properties,
            @Qualifier("callTaskDispatchExecutor") Executor dispatchExecutor
    ) {
        this.taskPartitionManager = taskPartitionManager;
        this.partitionSchedulerWorker = partitionSchedulerWorker;
        this.properties = properties;
        this.dispatchExecutor = dispatchExecutor;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.poll-interval:PT1S}")
    public void dispatchOwnedPartitions() {
        for (int partition : taskPartitionManager.ownedPartitions()) {
            submitPartition(partition);
        }
    }

    private void submitPartition(int partition) {
        AtomicBoolean running = runningPartitions.computeIfAbsent(partition, ignored -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            dispatchExecutor.execute(() -> {
                try {
                    drainPartition(partition);
                } finally {
                    running.set(false);
                }
            });
        } catch (RejectedExecutionException ex) {
            running.set(false);
            log.warn("Dispatch executor rejected partition {}", partition, ex);
        }
    }

    private void drainPartition(int partition) {
        for (int i = 0; i < properties.getMaxTasksPerPartitionTick(); i++) {
            if (!partitionSchedulerWorker.runPartition(partition)) {
                break;
            }
        }
    }
}
