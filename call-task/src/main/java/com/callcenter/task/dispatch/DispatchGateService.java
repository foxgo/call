package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.common.route.ShardKey;

public interface DispatchGateService {

    DispatchGateDecision evaluate(ShardKey shardKey, CallDialUnitEntity unit);
}
