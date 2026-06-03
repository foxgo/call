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

/**
 * 调度入口定时器。
 * 周期性扫描当前节点负责的 partition，并把实际调度工作分发到线程池。
 */
@Component
public class CallTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CallTaskDispatcher.class);

    private final TaskPartitionManager taskPartitionManager;
    private final PartitionSchedulerWorker partitionSchedulerWorker;
    private final CallTaskDispatchProperties properties;
    private final Executor dispatchExecutor;
    // 每个 partition 同一时刻只允许一个 worker 运行，避免重复 claim Redis 队列中的任务单元。
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
        // 单次 tick 允许连续拉取多个任务，减少定时调度的空转开销。
        for (int i = 0; i < properties.getMaxTasksPerPartitionTick(); i++) {
            if (!partitionSchedulerWorker.runPartition(partition)) {
                break;
            }
        }
    }
}
