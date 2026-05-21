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
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<CallEventOutboxEntity> events = repository.findPublishableBatch(now, properties.getBatchSize());
        for (CallEventOutboxEntity event : events) {
            publish(event, now);
        }
    }

    private void publish(CallEventOutboxEntity event, LocalDateTime now) {
        try {
            messagePublisher.publish(resolveTopic(event.getEventType()), event.getPartitionKey(), event.getPayload());
            repository.markPublished(event.getId(), now);
        } catch (RuntimeException exception) {
            int attempt = event.getAttemptCount() == null ? 1 : event.getAttemptCount() + 1;
            repository.markFailed(
                    event.getId(),
                    attempt,
                    rootMessage(exception),
                    now.plus(properties.getRetryBackoff())
            );
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "call_record_persisted" -> postprocessProperties.getTopics().getRecordPersisted();
            case "call_round_persisted" -> postprocessProperties.getTopics().getRoundPersisted();
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
