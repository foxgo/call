package com.callcenter.task.caller;

import com.callcenter.common.entity.CallTaskEntity;
import org.springframework.stereotype.Service;

@Service
/**
 * 把任务表中的主叫策略字段转换成运行时策略对象。
 * 调度线程只依赖这个聚合后的 policy，不直接关心任务表里的原始字段。
 */
public class TaskCallerIdPolicyService {

    public TaskCallerIdPolicy toPolicy(CallTaskEntity task) {
        return new TaskCallerIdPolicy(
                // HYBRID: 共享池 + 任务白名单混合。
                // SHARED_ONLY: 只从共享池选。
                // TASK_ONLY: 只从任务显式绑定的白名单里选。
                defaultString(task.getCallerIdMode(), "HYBRID"),
                defaultString(task.getOptimizationGoal(), "ANSWER"),
                defaultDouble(task.getAnswerWeight(), 1D),
                defaultDouble(task.getConversionWeight(), 0D),
                defaultDouble(task.getCostWeight(), 0D),
                defaultDouble(task.getRiskWeight(), 0D),
                // 当前版本 localPresenceEnabled / sameCallerCooldownSeconds 只进入 policy，
                // 但尚未在候选过滤或打分中真正生效，属于预留策略位。
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
