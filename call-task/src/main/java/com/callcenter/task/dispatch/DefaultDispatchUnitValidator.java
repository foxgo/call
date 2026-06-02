package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultDispatchUnitValidator implements DispatchUnitValidator {

    @Override
    public void validate(CallDialUnitEntity unit) {
        if (unit == null) {
            throw new DispatchPreparationException("unit is null");
        }
        if (unit.getId() == null) {
            throw new DispatchPreparationException("dialUnitId is null");
        }
        if (unit.getTenantId() == null) {
            throw new DispatchPreparationException("tenantId is null");
        }
        if (unit.getTaskId() == null) {
            throw new DispatchPreparationException("taskId is null");
        }
        if (isBlank(unit.getPhone())) {
            throw new DispatchPreparationException("phone is blank");
        }
        if (isBlank(unit.getDispatchToken())) {
            throw new DispatchPreparationException("dispatchToken is blank");
        }
        if (isBlank(unit.getSelectedCallerNumber())) {
            throw new DispatchPreparationException("selectedCallerNumber is blank");
        }
        if (isBlank(unit.getAttemptStage())) {
            throw new DispatchPreparationException("attemptStage is blank");
        }
        if (!isValidPhone(unit.getPhone())) {
            throw new DispatchPreparationException("phone format invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("\\d{11,20}");
    }
}
