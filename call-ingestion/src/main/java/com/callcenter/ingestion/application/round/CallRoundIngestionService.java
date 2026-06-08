package com.callcenter.ingestion.application.round;

import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.domain.round.CallRoundMessage;
import com.callcenter.ingestion.domain.shared.InboundMessage;
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
