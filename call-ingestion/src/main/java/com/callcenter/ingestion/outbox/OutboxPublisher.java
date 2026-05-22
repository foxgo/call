package com.callcenter.ingestion.outbox;

import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.ingestion.config.PostprocessProperties;
import com.callcenter.ingestion.mq.MessagePublisher;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final MessagePublisher messagePublisher;
    private final OutboxPublisherProperties properties;
    private final PostprocessProperties postprocessProperties;
    private final Clock clock;

    public OutboxPublisher(
            OutboxRepository repository,
            MessagePublisher messagePublisher,
            OutboxPublisherProperties properties,
            PostprocessProperties postprocessProperties
    ) {
        this(repository, messagePublisher, properties, postprocessProperties, Clock.systemUTC());
    }

    OutboxPublisher(
            OutboxRepository repository,
            MessagePublisher messagePublisher,
            OutboxPublisherProperties properties,
            PostprocessProperties postprocessProperties,
            Clock clock
    ) {
        this.repository = repository;
        this.messagePublisher = messagePublisher;
        this.properties = properties;
        this.postprocessProperties = postprocessProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${call.outbox.poll-interval:PT5S}")
    public void publishPendingBatch() {
        LocalDateTime batchNow = currentTime();
        repository.recoverStaleProcessingRows(batchNow.minus(properties.getProcessingTimeout()), batchNow);
        List<CallEventOutboxEntity> events =
                repository.claimPublishableBatch(batchNow, properties.getBatchSize(), properties.getMaxRetries());
        for (CallEventOutboxEntity event : events) {
            publish(event);
        }
    }

    private void publish(CallEventOutboxEntity event) {
        LocalDateTime now = currentTime();
        try {
            messagePublisher.publish(resolveTopic(event.getEventType()), event.getPartitionKey(), event.getPayload());
            repository.deleteProcessingById(event.getId());
        } catch (RuntimeException exception) {
            int attempt = event.getAttemptCount() == null ? 1 : event.getAttemptCount() + 1;
            LocalDateTime nextAttemptAt = now.plus(properties.getRetryBackoff());
            repository.markFailed(
                    event.getId(),
                    attempt,
                    rootMessage(exception),
                    now,
                    nextAttemptAt
            );
        }
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "call_record_persisted" -> postprocessProperties.getTopics().getRecordPersisted();
            default -> throw new IllegalArgumentException("不支持的 outbox 事件类型: " + eventType);
        };
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getName() + ": " + root.getMessage();
    }
}
