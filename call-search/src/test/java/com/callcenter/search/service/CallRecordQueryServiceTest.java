package com.callcenter.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.callcenter.search.model.CallRecordQueryRequest;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;

class CallRecordQueryServiceTest {

    @Test
    void shouldLogStructuredFailureWhenElasticsearchQueryFails() throws Exception {
        ElasticsearchClient client = new ElasticsearchClient(new FailingTransport());
        Logger logger = (Logger) LoggerFactory.getLogger(CallRecordQueryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        CallRecordQueryService service = new CallRecordQueryService(client);
        CallRecordQueryRequest request = new CallRecordQueryRequest();
        request.setPhone("13812345678");

        try {
            assertThatThrownBy(() -> service.query(9L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to query call records from Elasticsearch");

            assertThat(appender.list)
                    .hasSize(1)
                    .first()
                    .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                    .containsExactly(Level.WARN, "event=call_record_query_failed tenantId=9 phone=138****5678 reason=es down");
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static final class FailingTransport implements ElasticsearchTransport {

        private final JsonpMapper mapper = new JacksonJsonpMapper();

        @Override
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                RequestT request,
                Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                TransportOptions options
        ) throws IOException {
            throw new IOException("es down");
        }

        @Override
        public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
                RequestT request,
                Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                TransportOptions options
        ) {
            CompletableFuture<ResponseT> future = new CompletableFuture<>();
            future.completeExceptionally(new IOException("es down"));
            return future;
        }

        @Override
        public JsonpMapper jsonpMapper() {
            return mapper;
        }

        @Override
        public TransportOptions options() {
            return null;
        }

        @Override
        public void close() {
        }
    }
}
