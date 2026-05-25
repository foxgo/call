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

    public CallRoundIngestionService(
            ShardingRouter shardingRouter,
            CallRoundMysqlService callRoundMysqlService
    ) {
        this.shardingRouter = shardingRouter;
        this.callRoundMysqlService = callRoundMysqlService;
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
            return false;
        }
    }
}
