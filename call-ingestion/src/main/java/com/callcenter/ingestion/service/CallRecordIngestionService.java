package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.model.InboundMessage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 通话主记录入库服务。
 * 主流程是：根据租户/号码/时间路由分片，写入 call_record，再校验回合数是否完整。
 */
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
                    // 主记录落库成功后立即校验 round 数，尽早发现 record/round 分流写入的不一致。
                    ignored -> validatePersistedRoundCount(message)
            );
            return true;
        } catch (Exception exception) {
            // 这里返回 false 交给上层消费者触发 RocketMQ 重试，避免吞掉暂时性失败。
            return false;
        }
    }

    private void validatePersistedRoundCount(CallRecordMessage message) {
        if (message.roundTotal() == null) {
            // 老数据或上游未提供回合总数时不做强校验。
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
