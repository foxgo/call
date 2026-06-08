package com.callcenter.ingestion.application.record;

import com.callcenter.ingestion.application.port.RecordRepository;
import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.domain.service.CallRoundCountPolicy;
import com.callcenter.ingestion.domain.record.CallRecordMessage;
import com.callcenter.ingestion.domain.shared.InboundMessage;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * 通话主记录入库服务。
 * 主流程是：根据租户/号码/时间路由分片，写入 call_record，再校验回合数是否完整。
 */
@Service
public class CallRecordIngestionService {

    private final RecordRepository callRecordRepository;
    private final RoundRepository callRoundRepository;

    public CallRecordIngestionService(
            RecordRepository callRecordRepository,
            RoundRepository callRoundRepository
    ) {
        this.callRecordRepository = callRecordRepository;
        this.callRoundRepository = callRoundRepository;
    }

    public boolean process(InboundMessage<CallRecordMessage> inbound) {
        try {
            CallRecordMessage message = inbound.payload();
            callRecordRepository.save(message);
            // 主记录落库成功后立即校验 round 数，尽早发现 record/round 分流写入的不一致。
            validatePersistedRoundCount(message);
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
        long persistedRoundCount = callRoundRepository.countByCallId(
                message.tenantId(),
                message.callId(),
                toDateTime(message.startTime())
        );
        CallRoundCountPolicy.validate(message.roundTotal(), persistedRoundCount, message.callId());
    }

    private LocalDateTime toDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        return LocalDateTime.ofEpochSecond(
                epochMillis / 1000,
                (int) ((epochMillis % 1000) * 1_000_000),
                ZoneOffset.UTC
        );
    }
}
