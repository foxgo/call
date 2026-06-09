package com.callcenter.task.dispatch;

import com.callcenter.task.repository.entity.CallDialUnitEntity;

public interface DispatchUnitValidator {

    void validate(CallDialUnitEntity unit);
}
