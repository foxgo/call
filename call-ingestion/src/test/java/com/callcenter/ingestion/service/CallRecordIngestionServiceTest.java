package com.callcenter.ingestion.service;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.entity.CallRecordEntity;
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

class CallRecordIngestionServiceTest {

    @Test
    void shouldPersistRecordMessageWhenRoutingAndMysqlWriteSucceed() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRecordMysqlService callRecordMysqlService = mock(CallRecordMysqlService.class);
        FailurePublisher failurePublisher = mock(FailurePublisher.class);
        FailureClassifier failureClassifier = mock(FailureClassifier.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                shardingRouter,
                callRecordMysqlService,
                failurePublisher,
                failureClassifier
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(shardingRouter.routeRecord(eq(9L), eq("13800138000"), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRecordMysqlService.persistBatch(any(), any()))
                .thenReturn(List.of(mock(CallRecordEntity.class)));

        boolean processed = service.process(inbound);

        assertThat(processed).isTrue();
        verify(callRecordMysqlService).persistBatch(any(), eq(List.of(inbound.payload())));
        verify(failurePublisher, never()).publishDlq(any(), any());
    }

    @Test
    void shouldReturnFalseWhenMysqlWriteFailsWithRetryableError() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRecordMysqlService callRecordMysqlService = mock(CallRecordMysqlService.class);
        FailurePublisher failurePublisher = mock(FailurePublisher.class);
        FailureClassifier failureClassifier = mock(FailureClassifier.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                shardingRouter,
                callRecordMysqlService,
                failurePublisher,
                failureClassifier
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(shardingRouter.routeRecord(eq(9L), eq("13800138000"), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRecordMysqlService.persistBatch(any(), any()))
                .thenThrow(new IllegalStateException("mysql timeout"));
        when(failureClassifier.isRetryable(any())).thenReturn(true);

        boolean processed = service.process(inbound);

        assertThat(processed).isFalse();
        verify(failurePublisher, never()).publishDlq(any(), any());
    }

    @Test
    void shouldPublishDlqWhenMysqlWriteFailsWithNonRetryableError() {
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRecordMysqlService callRecordMysqlService = mock(CallRecordMysqlService.class);
        FailurePublisher failurePublisher = mock(FailurePublisher.class);
        FailureClassifier failureClassifier = mock(FailureClassifier.class);
        CallRecordIngestionService service = new CallRecordIngestionService(
                shardingRouter,
                callRecordMysqlService,
                failurePublisher,
                failureClassifier
        );
        InboundMessage<CallRecordMessage> inbound = recordInboundMessage();

        when(shardingRouter.routeRecord(eq(9L), eq("13800138000"), any()))
                .thenReturn(new ShardKey(9L, 0, 1, "202605"));
        when(callRecordMysqlService.persistBatch(any(), any()))
                .thenThrow(new IllegalArgumentException("invalid payload"));
        when(failureClassifier.isRetryable(any())).thenReturn(false);
        when(failurePublisher.publishDlq(eq(inbound), any())).thenReturn(true);

        boolean processed = service.process(inbound);

        assertThat(processed).isTrue();
        verify(failurePublisher).publishDlq(eq(inbound), any());
    }

    private static InboundMessage<CallRecordMessage> recordInboundMessage() {
        CallRecordMessage payload = new CallRecordMessage(1001L, 9L, 1L, "13800138000", "021", 1, 1L, 2L, 3, 2, null);
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
