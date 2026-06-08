package com.callcenter.ingestion.application.record;

import com.callcenter.ingestion.domain.record.CallRecordMessage;
import com.callcenter.ingestion.domain.shared.InboundMessage;
import com.callcenter.ingestion.domain.shared.MessageType;
import com.callcenter.ingestion.application.port.RecordRepository;
import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.domain.model.CallRecordData;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class CallRecordIngestionServiceTest {

    @Test
    void shouldPersistRecordMessageWhenRoutingAndMysqlWriteSucceed() {
        RecordRepository callRecordRepository = mock(RecordRepository.class);
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(callRecordRepository.save(any())).thenReturn(recordData());
        when(callRoundRepository.countByCallId(eq(9L), eq(1001L), any())).thenReturn(2L);

        boolean processed = service.process(inbound);

        assertThat(processed).isTrue();
        verify(callRecordRepository).save(eq(inbound.payload()));
        verify(callRoundRepository).countByCallId(eq(9L), eq(1001L), any());
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithRetryableError() {
        RecordRepository callRecordRepository = mock(RecordRepository.class);
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(callRecordRepository.save(any()))
                .thenThrow(new IllegalStateException("mysql timeout"));

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithNonRetryableError() {
        RecordRepository callRecordRepository = mock(RecordRepository.class);
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(callRecordRepository.save(any()))
                .thenThrow(new IllegalArgumentException("invalid payload"));

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
    }

    @Test
    void shouldReturnFalseWhenPersistedRoundCountDoesNotMatchRoundTotal() {
        RecordRepository callRecordRepository = mock(RecordRepository.class);
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(callRecordRepository.save(any())).thenReturn(recordData());
        when(callRoundRepository.countByCallId(eq(9L), eq(1001L), any())).thenReturn(1L);

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
        verify(callRoundRepository).countByCallId(eq(9L), eq(1001L), any());
    }

    private static InboundMessage<CallRecordMessage> recordInboundMessage() {
        CallRecordMessage payload = new CallRecordMessage(
                1001L,
                9L,
                1L,
                "13800138000",
                "021",
                1,
                1L,
                2L,
                3,
                2,
                "https://cdn.example.com/recordings/1001.mp3",
                1001,
                "callee busy",
                (byte) 1,
                (byte) 1,
                1500L,
                3L,
                4L,
                null
        );
        return InboundMessage.main(
                "call_record_ingest",
                0,
                3L,
                "9",
                MessageType.RECORD,
                "1001",
                payload
        );
    }

    private static CallRecordData recordData() {
        return new CallRecordData(
                1001L,
                9L,
                1L,
                "13800138000",
                "021",
                1,
                3,
                2,
                "https://cdn.example.com/recordings/1001.mp3",
                1001,
                "callee busy",
                (byte) 1,
                (byte) 1,
                1500L,
                LocalDateTime.of(2026, 5, 20, 10, 0, 1),
                LocalDateTime.of(2026, 5, 20, 10, 3),
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 3),
                LocalDateTime.of(2026, 5, 20, 10, 4)
        );
    }
}
