package com.callcenter.ingestion.application.round;

import com.callcenter.ingestion.domain.round.CallRoundMessage;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.domain.shared.InboundMessage;
import com.callcenter.ingestion.infrastructure.round.persistence.MybatisCallRoundRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRoundIngestionService {

    private final ShardingRouter shardingRouter;
    private final MybatisCallRoundRepository callRoundRepository;

    public CallRoundIngestionService(
            ShardingRouter shardingRouter,
            MybatisCallRoundRepository callRoundRepository
    ) {
        this.shardingRouter = shardingRouter;
        this.callRoundRepository = callRoundRepository;
    }

    public boolean process(InboundMessage<CallRoundMessage> inbound) {
        try {
            CallRoundMessage message = inbound.payload();
            ShardKey shardKey = shardingRouter.routeRound(
                    message.tenantId(),
                    message.callId(),
                    shardingRouter.toDateTime(message.startTime())
            );
            callRoundRepository.persistBatch(shardKey, List.of(message));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
