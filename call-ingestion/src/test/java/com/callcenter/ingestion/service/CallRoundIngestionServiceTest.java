package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.model.InboundMessage;
import com.callcenter.ingestion.model.MessageType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallRoundIngestionServiceTest {

    @Test
    void shouldPersistRoundMessageWhenRoutingAndMysqlWriteSucceed() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRoundMysqlService callRoundMysqlService = mock(CallRoundMysqlService.class);
        CallRoundIngestionService service = new CallRoundIngestionService(
                shardingRouter,
                callRoundMysqlService
        );
        InboundMessage<CallRoundMessage> inbound = roundInboundMessage();

        when(shardingRouter.routeRound(eq(9L), eq(1001L), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRoundMysqlService.persistBatch(any(), any()))
                .thenReturn(List.of(mock(CallRoundEntity.class)));

        boolean processed = service.process(inbound);

        assertThat(processed).isTrue();
        verify(callRoundMysqlService).persistBatch(any(), eq(List.of(inbound.payload())));
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithRetryableError() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRoundMysqlService callRoundMysqlService = mock(CallRoundMysqlService.class);
        CallRoundIngestionService service = new CallRoundIngestionService(
                shardingRouter,
                callRoundMysqlService
        );
        InboundMessage<CallRoundMessage> inbound = roundInboundMessage();

        when(shardingRouter.routeRound(eq(9L), eq(1001L), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRoundMysqlService.persistBatch(any(), any()))
                .thenThrow(new IllegalStateException("mysql timeout"));

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithNonRetryableError() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRoundMysqlService callRoundMysqlService = mock(CallRoundMysqlService.class);
        CallRoundIngestionService service = new CallRoundIngestionService(
                shardingRouter,
                callRoundMysqlService
        );
        InboundMessage<CallRoundMessage> inbound = roundInboundMessage();

        when(shardingRouter.routeRound(eq(9L), eq(1001L), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRoundMysqlService.persistBatch(any(), any()))
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
