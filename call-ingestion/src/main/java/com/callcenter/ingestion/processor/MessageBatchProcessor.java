package com.callcenter.ingestion.processor;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.config.WriteMetrics;
import com.callcenter.ingestion.retry.RetryExecutor;
import com.callcenter.ingestion.service.CallRecordIndexService;
import com.callcenter.ingestion.service.CallRecordMysqlService;
import com.callcenter.ingestion.service.CallRoundIndexService;
import com.callcenter.ingestion.service.CallRoundMysqlService;
import com.callcenter.ingestion.service.DlqPublisher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageBatchProcessor {

    private final ShardingRouter shardingRouter;
    private final CallRecordMysqlService callRecordMysqlService;
    private final CallRecordIndexService callRecordIndexService;
    private final CallRoundMysqlService callRoundMysqlService;
    private final CallRoundIndexService callRoundIndexService;
    private final RetryExecutor retryExecutor;
    private final DlqPublisher dlqPublisher;
    private final ExecutorService executorService;
    private final WriteMetrics writeMetrics;

    public MessageBatchProcessor(
            ShardingRouter shardingRouter,
            CallRecordMysqlService callRecordMysqlService,
            CallRecordIndexService callRecordIndexService,
            CallRoundMysqlService callRoundMysqlService,
            CallRoundIndexService callRoundIndexService,
            RetryExecutor retryExecutor,
            DlqPublisher dlqPublisher,
            ExecutorService executorService,
            WriteMetrics writeMetrics
    ) {
        this.shardingRouter = shardingRouter;
        this.callRecordMysqlService = callRecordMysqlService;
        this.callRecordIndexService = callRecordIndexService;
        this.callRoundMysqlService = callRoundMysqlService;
        this.callRoundIndexService = callRoundIndexService;
        this.retryExecutor = retryExecutor;
        this.dlqPublisher = dlqPublisher;
        this.executorService = executorService;
        this.writeMetrics = writeMetrics;
    }

    public void processRecordBatch(List<CallRecordMessage> messages) {
        Map<ShardKey, List<CallRecordMessage>> grouped = messages.stream()
                .collect(Collectors.groupingBy(message -> shardingRouter.routeRecord(
                        message.tenantId(),
                        message.phone(),
                        shardingRouter.toDateTime(message.startTime())
                )));

        List<? extends Future<?>> futures = grouped.entrySet().stream()
                .map(entry -> executorService.submit(() -> processRecordGroup(entry.getKey(), entry.getValue())))
                .toList();
        waitForTasks((List<Future<?>>) futures);
    }

    public void processRoundBatch(List<CallRoundMessage> messages) {
        Map<ShardKey, List<CallRoundMessage>> grouped = messages.stream()
                .collect(Collectors.groupingBy(message -> shardingRouter.routeRound(
                        message.tenantId(),
                        message.callId(),
                        shardingRouter.toDateTime(message.startTime())
                )));

        List<? extends Future<?>> futures = grouped.entrySet().stream()
                .map(entry -> executorService.submit(() -> processRoundGroup(entry.getKey(), entry.getValue())))
                .toList();
        waitForTasks((List<Future<?>>) futures);
    }

    private void processRecordGroup(ShardKey shardKey, List<CallRecordMessage> batch) {
        try {
            List<CallRecordEntity> entities = retryExecutor.call(
                    "record-mysql-batch",
                    () -> callRecordMysqlService.persistBatch(shardKey, batch)
            );
            retryExecutor.run("record-es-batch", () -> callRecordIndexService.indexBatch(entities));
        } catch (Exception exception) {
            writeMetrics.processErrorRate().increment();
            batch.forEach(message -> dlqPublisher.publishRecord(message, exception));
            log.error("Record batch failed for shard {}", shardKey, exception);
        }
    }

    private void processRoundGroup(ShardKey shardKey, List<CallRoundMessage> batch) {
        try {
            List<CallRoundEntity> entities = retryExecutor.call(
                    "round-mysql-batch",
                    () -> callRoundMysqlService.persistBatch(shardKey, batch)
            );
            retryExecutor.run("round-es-batch", () -> callRoundIndexService.indexBatch(entities));
        } catch (Exception exception) {
            writeMetrics.processErrorRate().increment();
            batch.forEach(message -> dlqPublisher.publishRound(message, exception));
            log.error("Round batch failed for shard {}", shardKey, exception);
        }
    }

    private void waitForTasks(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to synchronize virtual-thread tasks", exception);
            }
        }
    }
}
