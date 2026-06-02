package com.callcenter.task.dispatch;

public record DispatchGateDecision(boolean allowed, String reason) {

    public static DispatchGateDecision allow() {
        return new DispatchGateDecision(true, "ALLOW");
    }

    public static DispatchGateDecision reject(String reason) {
        return new DispatchGateDecision(false, reason);
    }
}
