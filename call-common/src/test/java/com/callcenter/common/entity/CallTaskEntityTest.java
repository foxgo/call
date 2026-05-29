package com.callcenter.common.entity;

import com.callcenter.common.enums.CallDialUnitStatus;
import com.callcenter.common.enums.CallTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallTaskEntityTest {

    @Test
    void shouldExposeTaskStatusesForLifecycleFlow() {
        assertEquals(CallTaskStatus.INIT, CallTaskStatus.valueOf("INIT"));
        assertEquals(CallTaskStatus.RUNNING, CallTaskStatus.valueOf("RUNNING"));
        assertEquals(CallDialUnitStatus.READY, CallDialUnitStatus.valueOf("READY"));
    }
}
