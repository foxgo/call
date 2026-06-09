package com.callcenter.task.dispatch;

import com.callcenter.task.entity.CallDialUnitEntity;
import com.callcenter.persistence.route.ShardKey;

public interface DispatchGateService {

    DispatchGateDecision evaluate(ShardKey shardKey, CallDialUnitEntity unit);
}
