package com.callcenter.task.caller;

import com.callcenter.common.entity.CallTaskEntity;
import org.springframework.stereotype.Service;

@Service
public class TaskCallerIdPolicyService {

    public TaskCallerIdPolicy toPolicy(CallTaskEntity task) {
        return new TaskCallerIdPolicy(
                defaultString(task.getCallerIdMode(), "HYBRID"),
                defaultString(task.getOptimizationGoal(), "ANSWER"),
                defaultDouble(task.getAnswerWeight(), 1D),
                defaultDouble(task.getConversionWeight(), 0D),
                defaultDouble(task.getCostWeight(), 0D),
                defaultDouble(task.getRiskWeight(), 0D),
                task.getLocalPresenceEnabled() != null && task.getLocalPresenceEnabled(),
                defaultInteger(task.getSameCallerCooldownSeconds(), 3600),
                defaultInteger(task.getMaxCallerExposurePerHour(), 200)
        );
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double defaultDouble(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static int defaultInteger(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
