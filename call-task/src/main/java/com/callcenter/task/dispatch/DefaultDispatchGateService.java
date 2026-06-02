package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.enums.CallTaskStatus;
import com.callcenter.common.route.ShardKey;
import com.callcenter.task.repository.CallTaskRepository;
import org.springframework.stereotype.Component;

@Component
public class DefaultDispatchGateService implements DispatchGateService {

    private final CallTaskRepository callTaskRepository;

    public DefaultDispatchGateService(CallTaskRepository callTaskRepository) {
        this.callTaskRepository = callTaskRepository;
    }

    @Override
    public DispatchGateDecision evaluate(ShardKey shardKey, CallDialUnitEntity unit) {
        CallTaskEntity task = callTaskRepository.findRequired(unit.getTenantId(), unit.getTaskId());
        if (!CallTaskStatus.RUNNING.name().equals(task.getStatus())) {
            return DispatchGateDecision.reject("TASK_NOT_RUNNING");
        }
        return DispatchGateDecision.allow();
    }
}
