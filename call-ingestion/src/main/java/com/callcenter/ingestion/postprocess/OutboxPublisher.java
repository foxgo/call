package com.callcenter.ingestion.postprocess;

import com.callcenter.ingestion.service.MessagePublisher;
import com.callcenter.ingestion.service.OutboxPublisherSettings;
import com.callcenter.ingestion.service.OutboxEventRepository;
import com.callcenter.ingestion.service.PostprocessSettings;
import com.callcenter.ingestion.model.OutboxEventData;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final MessagePublisher messagePublisher;
    private final OutboxPublisherSettings properties;
    private final PostprocessSettings postprocessSettings;
    private final Clock clock;

    public OutboxPublisher(
            OutboxEventRepository repository,
            MessagePublisher messagePublisher,
            OutboxPublisherSettings properties,
            PostprocessSettings postprocessSettings
    ) {
        this(repository, messagePublisher, properties, postprocessSettings, Clock.systemUTC());
    }

    OutboxPublisher(
            OutboxEventRepository repository,
            MessagePublisher messagePublisher,
            OutboxPublisherSettings properties,
            PostprocessSettings postprocessSettings,
            Clock clock
    ) {
        this.repository = repository;
        this.messagePublisher = messagePublisher;
        this.properties = properties;
        this.postprocessSettings = postprocessSettings;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${call.outbox.poll-interval:PT5S}")
    public void publishPendingBatch() {
        LocalDateTime batchNow = currentTime();
        repository.recoverStaleProcessingRows(batchNow.minus(properties.processingTimeout()), batchNow);
        List<OutboxEventData> events =
                repository.claimPublishableBatch(batchNow, properties.batchSize(), properties.maxRetries());
        for (OutboxEventData event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEventData event) {
        LocalDateTime now = currentTime();
        try {
            messagePublisher.publish(resolveTopic(event.eventType()), event.partitionKey(), event.payload());
            repository.deleteProcessingById(event.id());
        } catch (RuntimeException exception) {
            int attempt = event.attemptCount() == null ? 1 : event.attemptCount() + 1;
            LocalDateTime nextAttemptAt = now.plus(properties.retryBackoff());
            repository.markFailed(
                    event.id(),
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
            case "call_record_persisted" -> postprocessSettings.recordPersistedTopic();
            case "call_record_analysis_completed" -> postprocessSettings.analysisCompletedTopic();
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
