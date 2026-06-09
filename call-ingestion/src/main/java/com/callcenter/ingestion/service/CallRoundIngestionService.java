package com.callcenter.ingestion.service;

import com.callcenter.ingestion.service.RoundRepository;
import com.callcenter.ingestion.model.CallRoundMessage;
import com.callcenter.ingestion.model.InboundMessage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRoundIngestionService {

    private final RoundRepository callRoundRepository;

    public CallRoundIngestionService(
            RoundRepository callRoundRepository
    ) {
        this.callRoundRepository = callRoundRepository;
    }

    public boolean process(InboundMessage<CallRoundMessage> inbound) {
        try {
            callRoundRepository.saveBatch(List.of(inbound.payload()));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
