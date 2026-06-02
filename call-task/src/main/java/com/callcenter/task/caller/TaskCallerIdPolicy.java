package com.callcenter.task.caller;

public record TaskCallerIdPolicy(
        String callerIdMode,
        String optimizationGoal,
        double answerWeight,
        double conversionWeight,
        double costWeight,
        double riskWeight,
        boolean localPresenceEnabled,
        int sameCallerCooldownSeconds,
        int maxCallerExposurePerHour
) {
}
