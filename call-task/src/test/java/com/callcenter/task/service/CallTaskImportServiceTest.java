package com.callcenter.task.service;

import com.callcenter.task.repository.entity.CallTaskEntity;
import com.callcenter.task.repository.entity.CallTaskImportBatchEntity;
import com.callcenter.task.enums.CallTaskStatus;
import com.callcenter.persistence.route.ShardKey;
import com.callcenter.persistence.route.ShardingRouter;
import com.callcenter.persistence.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.dispatch.TaskActivationService;
import com.callcenter.task.model.ImportDialUnitItem;
import com.callcenter.task.model.ImportDialUnitsRequest;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskImportBatchRepository;
import com.callcenter.task.repository.CallTaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTaskImportServiceTest {

    @Test
    void shouldCreateImportBatchInsertPendingDialUnitsAndActivateRunningTask() {
        Fixture fixture = new Fixture();
        fixture.task.setStatus(CallTaskStatus.RUNNING.name());

        assertEquals("COMPLETED", fixture.service().importDialUnits(9L, 1001L, request()).status());

        verify(fixture.importBatchRepository).insert(any(CallTaskImportBatchEntity.class));
        verify(fixture.dialUnitRepository).batchInsert(eq(new ShardKey(9L, 0, 1, "dial")), any());
        verify(fixture.taskRepository).updateById(any(CallTaskEntity.class));
        verify(fixture.taskActivationService).activate(9L, 1001L);
    }

    @Test
    void shouldNotActivateTaskWhenImportedTaskIsNotRunning() {
        Fixture fixture = new Fixture();
        fixture.task.setStatus(CallTaskStatus.INIT.name());

        assertEquals("COMPLETED", fixture.service().importDialUnits(9L, 1001L, request()).status());

        verify(fixture.taskActivationService, never()).activate(any(), any());
    }

    private static ImportDialUnitsRequest request() {
        ImportDialUnitItem item = new ImportDialUnitItem();
        item.setPhone("13800138000");
        ImportDialUnitsRequest request = new ImportDialUnitsRequest();
        request.setUnits(List.of(item));
        return request;
    }

    private static final class Fixture {
        private final CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        private final CallTaskImportBatchRepository importBatchRepository = mock(CallTaskImportBatchRepository.class);
        private final CallDialUnitRepository dialUnitRepository = mock(CallDialUnitRepository.class);
        private final ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        private final ShardingRouter shardingRouter = mock(ShardingRouter.class);
        private final TaskActivationService taskActivationService = mock(TaskActivationService.class);
        private final CallTaskEntity task = new CallTaskEntity();

        private Fixture() {
            task.setId(1001L);
            task.setTenantId(9L);
            task.setTotalCount(0);
            when(taskRepository.findRequired(9L, 1001L)).thenReturn(task);
            when(idGenerator.nextId("1001:import")).thenReturn(2001L);
            when(idGenerator.nextId("13800138000")).thenReturn(3001L);
            when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
            when(dialUnitRepository.batchInsert(any(), any())).thenReturn(1);
        }

        private CallTaskImportService service() {
            return new CallTaskImportService(
                    taskRepository,
                    importBatchRepository,
                    dialUnitRepository,
                    idGenerator,
                    shardingRouter,
                    taskActivationService
            );
        }
    }
}
