package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.CallRoundData;
import com.callcenter.ingestion.model.CallRoundMessage;
import java.time.LocalDateTime;
import java.util.List;

public interface RoundRepository {

    List<CallRoundData> saveBatch(List<CallRoundMessage> messages);

    long countByCallId(long tenantId, long callId, LocalDateTime callStartedAt);

    List<CallRoundData> findByCallId(long tenantId, long callId, LocalDateTime callStartedAt);
}
