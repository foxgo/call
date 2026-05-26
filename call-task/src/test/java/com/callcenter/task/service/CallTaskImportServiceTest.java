package com.callcenter.task.service;

import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.entity.CallTaskImportBatchEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.common.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.model.ImportDialUnitItem;
import com.callcenter.task.model.ImportDialUnitsRequest;
import com.callcenter.task.repository.CallDialUnitRepository;
import com.callcenter.task.repository.CallTaskImportBatchRepository;
import com.callcenter.task.repository.CallTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallTaskImportServiceTest {

    @Test
    void shouldCreateImportBatchAndInsertPendingDialUnits() {
        CallTaskRepository taskRepository = mock(CallTaskRepository.class);
        CallTaskImportBatchRepository importBatchRepository = mock(CallTaskImportBatchRepository.class);
        CallDialUnitRepository dialUnitRepository = mock(CallDialUnitRepository.class);
        ShardedSnowflakeIdGenerator idGenerator = mock(ShardedSnowflakeIdGenerator.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);

        CallTaskEntity task = new CallTaskEntity();
        task.setId(1001L);
        task.setTenantId(9L);
        task.setTotalCount(0);
        when(taskRepository.findRequired(9L, 1001L)).thenReturn(task);
        when(idGenerator.nextId("1001:import")).thenReturn(2001L);
        when(idGenerator.nextId("13800138000")).thenReturn(3001L);
        when(shardingRouter.routeDialUnit(9L, 1001L)).thenReturn(new ShardKey(9L, 0, 1, "dial"));
        when(dialUnitRepository.batchInsert(any(), any())).thenReturn(1);

        CallTaskImportService service = new CallTaskImportService(
                taskRepository,
                importBatchRepository,
                dialUnitRepository,
                idGenerator,
                shardingRouter
        );

        ImportDialUnitItem item = new ImportDialUnitItem();
        item.setPhone("13800138000");
        ImportDialUnitsRequest request = new ImportDialUnitsRequest();
        request.setUnits(List.of(item));

        assertEquals("COMPLETED", service.importDialUnits(9L, 1001L, request).status());
        verify(importBatchRepository).insert(any(CallTaskImportBatchEntity.class));
        verify(dialUnitRepository).batchInsert(eq(new ShardKey(9L, 0, 1, "dial")), any());
        verify(taskRepository).updateById(any(CallTaskEntity.class));
    }
}
