package com.callcenter.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.callcenter.task.entity.CallTaskCallerIdBindingEntity;
import com.callcenter.task.mapper.CallTaskCallerIdBindingMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class CallTaskCallerIdBindingRepository {

    private final CallTaskCallerIdBindingMapper bindingMapper;

    public CallTaskCallerIdBindingRepository(CallTaskCallerIdBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

    public List<CallTaskCallerIdBindingEntity> listByTask(Long tenantId, Long taskId) {
        QueryWrapper<CallTaskCallerIdBindingEntity> query = new QueryWrapper<>();
        query.eq("tenant_id", tenantId)
                .eq("task_id", taskId)
                .orderByAsc("id");
        return bindingMapper.selectList(query);
    }
}
