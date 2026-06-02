package com.callcenter.task.dispatch;

import com.callcenter.common.entity.CallDialUnitEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultDispatchUnitValidatorTest {

    private final DefaultDispatchUnitValidator validator = new DefaultDispatchUnitValidator();

    @Test
    void shouldRejectMissingSelectedCallerNumber() {
        CallDialUnitEntity unit = preparedUnit();
        unit.setSelectedCallerNumber(" ");

        DispatchPreparationException ex = assertThrows(
                DispatchPreparationException.class,
                () -> validator.validate(unit)
        );

        org.junit.jupiter.api.Assertions.assertEquals("selectedCallerNumber is blank", ex.getMessage());
    }

    @Test
    void shouldRejectInvalidPhoneFormat() {
        CallDialUnitEntity unit = preparedUnit();
        unit.setPhone("abc");

        DispatchPreparationException ex = assertThrows(
                DispatchPreparationException.class,
                () -> validator.validate(unit)
        );

        org.junit.jupiter.api.Assertions.assertEquals("phone format invalid", ex.getMessage());
    }

    @Test
    void shouldAcceptPreparedUnit() {
        assertDoesNotThrow(() -> validator.validate(preparedUnit()));
    }

    private static CallDialUnitEntity preparedUnit() {
        CallDialUnitEntity unit = new CallDialUnitEntity();
        unit.setId(11L);
        unit.setTenantId(9L);
        unit.setTaskId(1001L);
        unit.setPhone("138001380011");
        unit.setDispatchToken("token-1");
        unit.setSelectedCallerNumber("02166668888");
        unit.setAttemptStage("FIRST_ATTEMPT");
        return unit;
    }
}
