package com.callcenter.task.caller;

import com.callcenter.common.entity.CallCallerIdStatsEntity;
import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.task.repository.CallCallerIdStatsRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

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
        Map<Long, CallCallerIdStatsEntity> statsByCaller = callCallerIdStatsRepository.findLatestByCallerIds(
                tenantId,
                candidates.stream().map(CallerIdCandidate::callerIdId).toList(),
                attemptStage.name()
        );
        return candidates.stream()
                .map(candidate -> toSelection(candidate, statsByCaller.get(candidate.callerIdId()), policy, attemptStage))
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
        double answerRate = attempts > 0 ? (double) answers / attempts : 0.5D;
        double successRate = attempts > 0 ? (double) successes / attempts : 0D;
        double avgTalkSeconds = answers > 0 ? (double) talkSeconds / answers : 0D;
        double conversionProxy = Math.min(avgTalkSeconds / 60D, 1D);
        double exposurePenalty = attempts > policy.maxCallerExposurePerHour()
                ? ((double) (attempts - policy.maxCallerExposurePerHour()) / policy.maxCallerExposurePerHour()) * 20D
                : 0D;
        double failurePenalty = failurePenalty(stats == null ? null : stats.getFailureCodeSummary());

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
        return Math.min(10D, failureCodeSummary.split(",").length * 2D);
    }
}
