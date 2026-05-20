package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.model.InboundMessage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRecordIngestionService {

    private final ShardingRouter shardingRouter;
    private final CallRecordMysqlService callRecordMysqlService;
    private final FailurePublisher failurePublisher;
    private final FailureClassifier failureClassifier;

    public CallRecordIngestionService(
            ShardingRouter shardingRouter,
            CallRecordMysqlService callRecordMysqlService,
            FailurePublisher failurePublisher,
            FailureClassifier failureClassifier
    ) {
        this.shardingRouter = shardingRouter;
        this.callRecordMysqlService = callRecordMysqlService;
        this.failurePublisher = failurePublisher;
        this.failureClassifier = failureClassifier;
    }

    public boolean process(InboundMessage<CallRecordMessage> inbound) {
        try {
            CallRecordMessage message = inbound.payload();
            ShardKey shardKey = shardingRouter.routeRecord(
                    message.tenantId(),
                    message.phone(),
                    shardingRouter.toDateTime(message.startTime())
            );
            callRecordMysqlService.persistBatch(shardKey, List.of(message));
            return true;
        } catch (Exception exception) {
            if (failureClassifier.isRetryable(exception)) {
                return false;
            }
            return failurePublisher.publishDlq(inbound, exception);
        }
    }
}
