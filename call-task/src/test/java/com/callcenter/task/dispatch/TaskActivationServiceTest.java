package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.task.repository.CallTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskActivationServiceTest {

    @Test
    void shouldActivateRunningTaskIntoOwnedPartitionQueue() {
        CapturingActiveTaskQueue queue = new CapturingActiveTaskQueue();
        TaskActivationService service = new TaskActivationService(queue);
        TaskActivationRequest request = new TaskActivationRequest(9L, 1001L, 1, 8, 7);

        service.activate(request);

        assertEquals(1001L, queue.metaTaskId);
        assertEquals(9L, queue.metaTenantId);
        assertEquals(1, queue.metaPriority);
        assertEquals(8, queue.metaWeight);
        assertEquals(7, queue.metaPartition);
        assertEquals(7, queue.activePartition);
        assertEquals(1001L, queue.activeTaskId);
        assertEquals(0L, queue.activeFairScore);
    }

    @Test
    void shouldUseTaskPriorityWhenActivatingByTaskId() {
        CapturingActiveTaskQueue queue = new CapturingActiveTaskQueue();
        CallTaskRepository repository = mock(CallTaskRepository.class);
        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);
        task.setPriority(1);
        when(repository.findRequired(9L, 1001L)).thenReturn(task);

        TaskActivationService service = new TaskActivationService(queue, repository);

        service.activate(9L, 1001L);

        assertEquals(1, queue.metaPriority);
        assertEquals(16, queue.metaWeight);
        assertEquals(new TaskPartitioner(128).partitionOf(1001L), queue.metaPartition);
    }

    private static final class CapturingActiveTaskQueue extends ActiveTaskQueue {

        private Long metaTaskId;
        private Long metaTenantId;
        private int metaPriority;
        private int metaWeight;
        private int metaPartition;
        private int activePartition;
        private Long activeTaskId;
        private long activeFairScore;

        private CapturingActiveTaskQueue() {
            super(new StringRedisTemplate());
        }

        @Override
        public void upsertMeta(Long taskId, Long tenantId, int priority, int weight, int partition) {
            this.metaTaskId = taskId;
            this.metaTenantId = tenantId;
            this.metaPriority = priority;
            this.metaWeight = weight;
            this.metaPartition = partition;
        }

        @Override
        public void activate(int partition, Long taskId, long fairScore) {
            this.activePartition = partition;
            this.activeTaskId = taskId;
            this.activeFairScore = fairScore;
        }
    }
}
