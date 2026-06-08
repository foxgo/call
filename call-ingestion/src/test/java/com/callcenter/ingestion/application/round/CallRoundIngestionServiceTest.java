package com.callcenter.ingestion.application.round;

import com.callcenter.ingestion.domain.round.CallRoundMessage;
import com.callcenter.ingestion.domain.shared.InboundMessage;
import com.callcenter.ingestion.domain.shared.MessageType;
import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.domain.model.CallRoundData;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallRoundIngestionServiceTest {

    @Test
    void shouldPersistRoundMessageWhenRoutingAndMysqlWriteSucceed() {
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallRoundIngestionService service = new CallRoundIngestionService(callRoundRepository);
        InboundMessage<CallRoundMessage> inbound = roundInboundMessage();

        when(callRoundRepository.saveBatch(any()))
                .thenReturn(List.of(mock(CallRoundData.class)));

        boolean processed = service.process(inbound);

        assertThat(processed).isTrue();
        verify(callRoundRepository).saveBatch(eq(List.of(inbound.payload())));
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithRetryableError() {
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallRoundIngestionService service = new CallRoundIngestionService(callRoundRepository);
        InboundMessage<CallRoundMessage> inbound = roundInboundMessage();

        when(callRoundRepository.saveBatch(any()))
                .thenThrow(new IllegalStateException("mysql timeout"));

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithNonRetryableError() {
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallRoundIngestionService service = new CallRoundIngestionService(callRoundRepository);
        InboundMessage<CallRoundMessage> inbound = roundInboundMessage();

        when(callRoundRepository.saveBatch(any()))
                .thenThrow(new IllegalArgumentException("invalid payload"));

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
    }

    private static InboundMessage<CallRoundMessage> roundInboundMessage() {
        CallRoundMessage payload = new CallRoundMessage(77L, 9L, 1001L, 1, "AGENT", "hi", "GREETING", 1L);
        return InboundMessage.main(
                "call_round_ingest",
                0,
                4L,
                "9",
                MessageType.ROUND,
                "1001:77",
                payload
        );
    }
}
