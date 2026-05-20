package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.model.InboundMessage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRoundIngestionService {

    private final ShardingRouter shardingRouter;
    private final CallRoundMysqlService callRoundMysqlService;
    private final FailurePublisher failurePublisher;
    private final FailureClassifier failureClassifier;

    public CallRoundIngestionService(
            ShardingRouter shardingRouter,
            CallRoundMysqlService callRoundMysqlService,
            FailurePublisher failurePublisher,
            FailureClassifier failureClassifier
    ) {
        this.shardingRouter = shardingRouter;
        this.callRoundMysqlService = callRoundMysqlService;
        this.failurePublisher = failurePublisher;
        this.failureClassifier = failureClassifier;
    }

    public boolean process(InboundMessage<CallRoundMessage> inbound) {
        try {
            CallRoundMessage message = inbound.payload();
            ShardKey shardKey = shardingRouter.routeRound(
                    message.tenantId(),
                    message.callId(),
                    shardingRouter.toDateTime(message.startTime())
            );
            callRoundMysqlService.persistBatch(shardKey, List.of(message));
            return true;
        } catch (Exception exception) {
            if (failureClassifier.isRetryable(exception)) {
                return false;
            }
            return failurePublisher.publishDlq(inbound, exception);
        }
    }
}
