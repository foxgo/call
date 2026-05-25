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
    private final CallRoundMysqlService callRoundMysqlService;

    public CallRecordIngestionService(
            ShardingRouter shardingRouter,
            CallRecordMysqlService callRecordMysqlService,
            CallRoundMysqlService callRoundMysqlService
    ) {
        this.shardingRouter = shardingRouter;
        this.callRecordMysqlService = callRecordMysqlService;
        this.callRoundMysqlService = callRoundMysqlService;
    }

    public boolean process(InboundMessage<CallRecordMessage> inbound) {
        try {
            CallRecordMessage message = inbound.payload();
            ShardKey shardKey = shardingRouter.routeRecord(
                    message.tenantId(),
                    message.phone(),
                    shardingRouter.toDateTime(message.startTime())
            );
            callRecordMysqlService.persistBatch(
                    shardKey,
                    List.of(message),
                    ignored -> validatePersistedRoundCount(message)
            );
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void validatePersistedRoundCount(CallRecordMessage message) {
        if (message.roundTotal() == null) {
            return;
        }
        ShardKey roundShardKey = shardingRouter.routeRound(
                message.tenantId(),
                message.callId(),
                shardingRouter.toDateTime(message.startTime())
        );
        long persistedRoundCount = callRoundMysqlService.countByCallId(roundShardKey, message.callId());
        if (persistedRoundCount != message.roundTotal()) {
            throw new IllegalStateException(
                    "call_round persisted count mismatch, callId=%d, expected=%d, actual=%d".formatted(
                            message.callId(),
                            message.roundTotal(),
                            persistedRoundCount
                    )
            );
        }
    }
}
