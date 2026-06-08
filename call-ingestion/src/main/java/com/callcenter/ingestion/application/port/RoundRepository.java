package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.model.CallRoundData;
import com.callcenter.ingestion.domain.round.CallRoundMessage;
import java.time.LocalDateTime;
import java.util.List;

public interface RoundRepository {

    List<CallRoundData> saveBatch(List<CallRoundMessage> messages);

    long countByCallId(long tenantId, long callId, LocalDateTime callStartedAt);

    List<CallRoundData> findByCallId(long tenantId, long callId, LocalDateTime callStartedAt);
}
