package com.callcenter.task.caller;

import com.callcenter.task.entity.CallCallerIdStatsEntity;
import com.callcenter.task.entity.CallDialUnitEntity;
import com.callcenter.task.repository.CallCallerIdStatsRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 外显号选择器。
 * 基于历史接通/转化/失败分布、成本和风险等指标，对候选外显号做一次打分排序。
 */
@Service
public class CallerIdSelector {

    private final CallCallerIdStatsRepository callCallerIdStatsRepository;

    public CallerIdSelector(CallCallerIdStatsRepository callCallerIdStatsRepository) {
        this.callCallerIdStatsRepository = callCallerIdStatsRepository;
    }

    public Optional<CallerIdSelection> select(
            Long tenantId,
            CallDialUnitEntity dialUnit,
            TaskCallerIdPolicy policy,
            List<CallerIdCandidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        AttemptStage attemptStage = AttemptStage.fromRetryCount(dialUnit.getRetryCount());
        // 统计数据按首呼/重试分开查询，避免把重试阶段的失败率污染首呼阶段的判断。
        Map<Long, CallCallerIdStatsEntity> statsByCaller = callCallerIdStatsRepository.findLatestByCallerIds(
                tenantId,
                candidates.stream().map(CallerIdCandidate::callerIdId).toList(),
                attemptStage.name()
        );
        return selectWithStats(dialUnit, policy, candidates, statsByCaller);
    }

    public Optional<CallerIdSelection> selectWithStats(
            CallDialUnitEntity dialUnit,
            TaskCallerIdPolicy policy,
            List<CallerIdCandidate> candidates,
            Map<Long, CallCallerIdStatsEntity> statsByCaller
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, CallCallerIdStatsEntity> stats = statsByCaller == null ? Map.of() : statsByCaller;
        AttemptStage attemptStage = AttemptStage.fromRetryCount(dialUnit.getRetryCount());
        return candidates.stream()
                // 每个候选号码独立打分，最后选择得分最高的一个。
                .map(candidate -> toSelection(candidate, stats.get(candidate.callerIdId()), policy, attemptStage))
                .max(Comparator.comparingDouble(CallerIdSelection::score));
    }

    private CallerIdSelection toSelection(
            CallerIdCandidate candidate,
            CallCallerIdStatsEntity stats,
            TaskCallerIdPolicy policy,
            AttemptStage attemptStage
    ) {
        long attempts = stats == null || stats.getAttemptCount() == null ? 0L : stats.getAttemptCount();
        long answers = stats == null || stats.getAnswerCount() == null ? 0L : stats.getAnswerCount();
        long successes = stats == null || stats.getSuccessCount() == null ? 0L : stats.getSuccessCount();
        long talkSeconds = stats == null || stats.getTotalTalkSeconds() == null ? 0L : stats.getTotalTalkSeconds();
        // 没有历史数据时给一个中性接通率，避免新外显号永远因为冷启动被排在末尾。
        double answerRate = attempts > 0 ? (double) answers / attempts : 0.5D;
        double successRate = attempts > 0 ? (double) successes / attempts : 0D;
        double avgTalkSeconds = answers > 0 ? (double) talkSeconds / answers : 0D;
        // 目前 conversionProxy 用平均通话时长近似表示“潜在转化质量”，不是实际业务转化率。
        double conversionProxy = Math.min(avgTalkSeconds / 60D, 1D);
        // 暴露次数超过策略阈值后开始加罚，控制单个外显号在短时间内被过度使用。
        double exposurePenalty = attempts > policy.maxCallerExposurePerHour()
                ? ((double) (attempts - policy.maxCallerExposurePerHour()) / policy.maxCallerExposurePerHour()) * 20D
                : 0D;
        double failurePenalty = failurePenalty(stats == null ? null : stats.getFailureCodeSummary());

        // 该分数是启发式排序值，不要求有绝对业务含义，只要求能稳定表达优先级。
        // 当前公式：
        // score =
        //   100 * answerWeight * answerRate
        // +  40 * conversionWeight * conversionProxy
        // +  20 * riskWeight * trustScore
        // -  20 * costWeight * costScore
        // +  10 * successRate
        // +  priorityBoost
        // -  exposurePenalty
        // -  failurePenalty
        double score = 100D * policy.answerWeight() * answerRate
                + 40D * policy.conversionWeight() * conversionProxy
                + 20D * policy.riskWeight() * candidate.trustScore()
                - 20D * policy.costWeight() * candidate.costScore()
                + 10D * successRate
                + candidate.priorityBoost()
                - exposurePenalty
                - failurePenalty;
        String reason = "stage=%s,answerRate=%.2f,successRate=%.2f,boost=%d".formatted(
                attemptStage.name(),
                answerRate,
                successRate,
                candidate.priorityBoost()
        );
        return new CallerIdSelection(candidate.callerIdId(), candidate.callerId(), attemptStage, score, reason);
    }

    private static double failurePenalty(String failureCodeSummary) {
        if (failureCodeSummary == null || failureCodeSummary.isBlank()) {
            return 0D;
        }
        // 当前实现按失败码种类数线性惩罚，同一失败码重复出现不会额外增加惩罚权重。
        return Math.min(10D, failureCodeSummary.split(",").length * 2D);
    }
}
