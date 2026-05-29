package com.callcenter.task.dispatch;

import com.callcenter.task.config.CallTaskDispatchProperties;
import com.callcenter.task.repository.CallDialUnitRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingDialUnitActivationJob {

    private final TaskPartitionManager taskPartitionManager;
    private final CallDialUnitRepository callDialUnitRepository;
    private final TaskActivationService taskActivationService;
    private final TaskPartitioner taskPartitioner;
    private final CallTaskDispatchProperties properties;

    public PendingDialUnitActivationJob(
            TaskPartitionManager taskPartitionManager,
            CallDialUnitRepository callDialUnitRepository,
            TaskActivationService taskActivationService,
            CallTaskDispatchProperties properties
    ) {
        this.taskPartitionManager = taskPartitionManager;
        this.callDialUnitRepository = callDialUnitRepository;
        this.taskActivationService = taskActivationService;
        this.taskPartitioner = new TaskPartitioner(properties.getPartitionCount());
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${call.task.dispatch.retry-scan-interval:PT5S}")
    public void activateDuePendingTasks() {
        Set<Integer> ownedPartitions = taskPartitionManager.ownedPartitions().stream().collect(Collectors.toSet());
        if (ownedPartitions.isEmpty()) {
            return;
        }
        for (CallDialUnitRepository.DuePendingTask task : callDialUnitRepository.findDuePendingTasks(
                LocalDateTime.now(ZoneOffset.UTC),
                properties.getPreloadBatchSize()
        )) {
            if (ownedPartitions.contains(taskPartitioner.partitionOf(task.taskId()))) {
                taskActivationService.activate(task.tenantId(), task.taskId());
            }
        }
    }
}
