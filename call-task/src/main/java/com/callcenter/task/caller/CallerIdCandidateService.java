package com.callcenter.task.caller;

import com.callcenter.task.repository.entity.CallCallerIdEntity;
import com.callcenter.task.repository.entity.CallTaskCallerIdBindingEntity;
import com.callcenter.task.repository.CallCallerIdRepository;
import com.callcenter.task.repository.CallTaskCallerIdBindingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
/**
 * 主叫号码候选集构建器。
 * 负责把共享号码池、任务白名单、任务黑名单和号码冷却状态合并成最终可参与打分的候选列表。
 */
public class CallerIdCandidateService {

    private static final String ACTIVE = "ACTIVE";
    private static final String SHARED = "SHARED";
    private static final String ALLOW = "ALLOW";
    private static final String DENY = "DENY";

    private final CallCallerIdRepository callCallerIdRepository;
    private final CallTaskCallerIdBindingRepository bindingRepository;

    public CallerIdCandidateService(
            CallCallerIdRepository callCallerIdRepository,
            CallTaskCallerIdBindingRepository bindingRepository
    ) {
        this.callCallerIdRepository = callCallerIdRepository;
        this.bindingRepository = bindingRepository;
    }

    public List<CallerIdCandidate> listCandidates(
            Long tenantId,
            Long taskId,
            TaskCallerIdPolicy policy,
            LocalDateTime now
    ) {
        List<CallTaskCallerIdBindingEntity> bindings = bindingRepository.listByTask(tenantId, taskId);
        // DENY 绑定优先级最高，命中后即使该号码同时出现在共享池或白名单中也要移除。
        Set<Long> denyIds = bindingIds(bindings, DENY);
        // ALLOW 绑定除了表示“允许参与选择”，还可以附带 priorityBoost 影响最终打分。
        Map<Long, Integer> allowBoosts = bindingBoosts(bindings, ALLOW);
        List<CallCallerIdEntity> sharedCallers = callCallerIdRepository.listActiveByTenantAndPoolType(tenantId, SHARED);
        List<CallCallerIdEntity> allowListedCallers = callCallerIdRepository.listByIds(tenantId, new ArrayList<>(allowBoosts.keySet()));

        LinkedHashMap<Long, CallerIdCandidate> merged = new LinkedHashMap<>();
        String mode = policy.callerIdMode();
        // 选择模式决定候选来源：
        // SHARED_ONLY -> 只看共享池
        // TASK_ONLY -> 只看任务白名单
        // HYBRID -> 两者都看，最后按 callerId 去重合并
        if ("SHARED_ONLY".equalsIgnoreCase(mode) || "HYBRID".equalsIgnoreCase(mode)) {
            for (CallCallerIdEntity caller : sharedCallers) {
                maybeAddCandidate(merged, caller, 0, denyIds, now);
            }
        }
        if ("TASK_ONLY".equalsIgnoreCase(mode) || "HYBRID".equalsIgnoreCase(mode)) {
            for (CallCallerIdEntity caller : allowListedCallers) {
                maybeAddCandidate(merged, caller, allowBoosts.getOrDefault(caller.getId(), 0), denyIds, now);
            }
        }
        return List.copyOf(merged.values());
    }

    private static void maybeAddCandidate(
            Map<Long, CallerIdCandidate> merged,
            CallCallerIdEntity caller,
            int priorityBoost,
            Set<Long> denyIds,
            LocalDateTime now
    ) {
        if (caller == null || caller.getId() == null) {
            return;
        }
        // 非 ACTIVE 号码直接剔除，不参与任何后续打分。
        if (!ACTIVE.equalsIgnoreCase(caller.getStatus())) {
            return;
        }
        if (denyIds.contains(caller.getId())) {
            merged.remove(caller.getId());
            return;
        }
        // 号码处于冷却窗口时不可再次使用，用于承接异常回写后的临时摘除。
        if (caller.getCooldownUntil() != null && caller.getCooldownUntil().isAfter(now)) {
            return;
        }
        merged.put(caller.getId(), new CallerIdCandidate(
                caller.getId(),
                caller.getCallerId(),
                caller.getPoolType(),
                caller.getCostScore() == null ? 0D : caller.getCostScore(),
                caller.getTrustScore() == null ? 0D : caller.getTrustScore(),
                priorityBoost
        ));
    }

    private static Set<Long> bindingIds(List<CallTaskCallerIdBindingEntity> bindings, String bindingType) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CallTaskCallerIdBindingEntity binding : bindings) {
            if (bindingType.equalsIgnoreCase(binding.getBindingType()) && binding.getCallerIdId() != null) {
                ids.add(binding.getCallerIdId());
            }
        }
        return ids;
    }

    private static Map<Long, Integer> bindingBoosts(List<CallTaskCallerIdBindingEntity> bindings, String bindingType) {
        Map<Long, Integer> boosts = new LinkedHashMap<>();
        for (CallTaskCallerIdBindingEntity binding : bindings) {
            if (bindingType.equalsIgnoreCase(binding.getBindingType()) && binding.getCallerIdId() != null) {
                // 同一个 callerId 多次绑定时以后出现的值为准，便于通过后插入规则覆盖旧配置。
                boosts.put(binding.getCallerIdId(), binding.getPriorityBoost() == null ? 0 : binding.getPriorityBoost());
            }
        }
        return boosts;
    }
}
