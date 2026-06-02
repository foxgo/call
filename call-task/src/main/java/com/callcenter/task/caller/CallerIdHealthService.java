package com.callcenter.task.caller;

import com.callcenter.common.entity.CallCallerIdStatsEntity;
import com.callcenter.common.util.ShardedSnowflakeIdGenerator;
import com.callcenter.task.repository.CallCallerIdStatsRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class CallerIdHealthService {

    private final CallCallerIdStatsRepository callCallerIdStatsRepository;
    private final ShardedSnowflakeIdGenerator idGenerator;

    public CallerIdHealthService(
            CallCallerIdStatsRepository callCallerIdStatsRepository,
            ShardedSnowflakeIdGenerator idGenerator
    ) {
        this.callCallerIdStatsRepository = callCallerIdStatsRepository;
        this.idGenerator = idGenerator;
    }

    public void recordFeedback(CallerIdHealthEvent event) {
        LocalDateTime bucket = bucketStart(LocalDateTime.now());
        CallCallerIdStatsEntity stats = callCallerIdStatsRepository.findBucket(
                event.tenantId(),
                event.callerIdId(),
                event.attemptStage().name(),
                bucket
        ).orElseGet(() -> newBucket(event, bucket));

        stats.setAttemptCount(defaultLong(stats.getAttemptCount()) + 1L);
        if (event.ringDurationSeconds() != null && event.ringDurationSeconds() > 0) {
            stats.setRingCount(defaultLong(stats.getRingCount()) + 1L);
        }
        if (event.success() || (event.talkDurationSeconds() != null && event.talkDurationSeconds() > 0)) {
            stats.setAnswerCount(defaultLong(stats.getAnswerCount()) + 1L);
        }
        if (event.success()) {
            stats.setSuccessCount(defaultLong(stats.getSuccessCount()) + 1L);
        }
        if (event.talkDurationSeconds() != null && event.talkDurationSeconds() > 0) {
            stats.setTotalTalkSeconds(defaultLong(stats.getTotalTalkSeconds()) + event.talkDurationSeconds());
        }
        if (event.failureCode() != null && !event.failureCode().isBlank()) {
            String existing = stats.getFailureCodeSummary();
            stats.setFailureCodeSummary(existing == null || existing.isBlank()
                    ? event.failureCode()
                    : existing + "," + event.failureCode());
        }
        stats.setHealthScore(calculateHealthScore(stats));
        stats.setUpdatedAt(LocalDateTime.now());
        callCallerIdStatsRepository.upsert(stats);
    }

    private CallCallerIdStatsEntity newBucket(CallerIdHealthEvent event, LocalDateTime bucket) {
        CallCallerIdStatsEntity entity = new CallCallerIdStatsEntity();
        entity.setId(idGenerator.nextId(event.tenantId() + ":" + event.callerIdId() + ":" + event.attemptStage().name() + ":" + bucket));
        entity.setTenantId(event.tenantId());
        entity.setCallerIdId(event.callerIdId());
        entity.setAttemptStage(event.attemptStage().name());
        entity.setTimeBucket(bucket);
        entity.setAttemptCount(0L);
        entity.setRingCount(0L);
        entity.setAnswerCount(0L);
        entity.setSuccessCount(0L);
        entity.setTotalTalkSeconds(0L);
        entity.setFailureCodeSummary(null);
        entity.setHealthScore(0D);
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static double calculateHealthScore(CallCallerIdStatsEntity stats) {
        long attempts = defaultLong(stats.getAttemptCount());
        long answers = defaultLong(stats.getAnswerCount());
        long totalTalkSeconds = defaultLong(stats.getTotalTalkSeconds());
        double answerRate = attempts > 0 ? (double) answers / attempts : 0D;
        double avgTalkSeconds = answers > 0 ? (double) totalTalkSeconds / answers : 0D;
        double talkContribution = Math.min(avgTalkSeconds / 60D, 1D) * 20D;
        double failurePenalty = stats.getFailureCodeSummary() == null || stats.getFailureCodeSummary().isBlank()
                ? 0D
                : Math.min(15D, stats.getFailureCodeSummary().split(",").length * 1.5D);
        return answerRate * 100D + talkContribution - failurePenalty;
    }

    private static long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private static LocalDateTime bucketStart(LocalDateTime time) {
        return time.withMinute(0).withSecond(0).withNano(0);
    }
}
