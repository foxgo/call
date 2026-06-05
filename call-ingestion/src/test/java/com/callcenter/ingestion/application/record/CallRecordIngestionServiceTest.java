package com.callcenter.ingestion.application.record;

import com.callcenter.ingestion.domain.record.CallRecordMessage;
import com.callcenter.ingestion.domain.shared.InboundMessage;
import com.callcenter.ingestion.domain.shared.MessageType;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.infrastructure.record.persistence.CallRecordEntity;
import com.callcenter.ingestion.infrastructure.record.persistence.MybatisCallRecordRepository;
import com.callcenter.ingestion.infrastructure.round.persistence.MybatisCallRoundRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class CallRecordIngestionServiceTest {

    @Test
    void shouldPersistRecordMessageWhenRoutingAndMysqlWriteSucceed() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        MybatisCallRecordRepository callRecordRepository = mock(MybatisCallRecordRepository.class);
        MybatisCallRoundRepository callRoundRepository = mock(MybatisCallRoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                shardingRouter,
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(shardingRouter.routeRecord(eq(9L), eq("13800138000"), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(shardingRouter.routeRound(eq(9L), eq(1001L), any()))
                .thenReturn(new ShardKey(9L, 0, 3, "202605"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<List<CallRecordEntity>> beforeOutboxInsert =
                    invocation.getArgument(2, java.util.function.Consumer.class);
            List<CallRecordEntity> entities = List.of(mock(CallRecordEntity.class));
            beforeOutboxInsert.accept(entities);
            return entities;
        }).when(callRecordRepository).persistBatch(any(), any(), any());
        when(callRoundRepository.countByCallId(any(), eq(1001L))).thenReturn(2L);

        boolean processed = service.process(inbound);

        assertThat(processed).isTrue();
        verify(callRecordRepository).persistBatch(any(), eq(List.of(inbound.payload())), any());
        verify(callRoundRepository).countByCallId(any(), eq(1001L));
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithRetryableError() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        MybatisCallRecordRepository callRecordRepository = mock(MybatisCallRecordRepository.class);
        MybatisCallRoundRepository callRoundRepository = mock(MybatisCallRoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                shardingRouter,
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(shardingRouter.routeRecord(eq(9L), eq("13800138000"), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRecordRepository.persistBatch(any(), any(), any()))
                .thenThrow(new IllegalStateException("mysql timeout"));

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithNonRetryableError() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        MybatisCallRecordRepository callRecordRepository = mock(MybatisCallRecordRepository.class);
        MybatisCallRoundRepository callRoundRepository = mock(MybatisCallRoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                shardingRouter,
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(shardingRouter.routeRecord(eq(9L), eq("13800138000"), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRecordRepository.persistBatch(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("invalid payload"));

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
    }

    @Test
    void shouldReturnFalseWhenPersistedRoundCountDoesNotMatchRoundTotal() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        MybatisCallRecordRepository callRecordRepository = mock(MybatisCallRecordRepository.class);
        MybatisCallRoundRepository callRoundRepository = mock(MybatisCallRoundRepository.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                shardingRouter,
                callRecordRepository,
                callRoundRepository
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(shardingRouter.routeRecord(eq(9L), eq("13800138000"), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(shardingRouter.routeRound(eq(9L), eq(1001L), any()))
                .thenReturn(new ShardKey(9L, 0, 3, "202605"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<List<CallRecordEntity>> beforeOutboxInsert =
                    invocation.getArgument(2, java.util.function.Consumer.class);
            List<CallRecordEntity> entities = List.of(mock(CallRecordEntity.class));
            beforeOutboxInsert.accept(entities);
            return entities;
        }).when(callRecordRepository).persistBatch(any(), any(), any());
        when(callRoundRepository.countByCallId(any(), eq(1001L))).thenReturn(1L);

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
        verify(callRoundRepository).countByCallId(any(), eq(1001L));
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
}
