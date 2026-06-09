package com.callcenter.observability.logging.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.callcenter.observability.logging.LogSanitizer;
import com.callcenter.observability.logging.StructuredLogFields;
import com.callcenter.observability.logging.config.LoggingProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateRequestIdAndPopulateMdc() throws ServletException, IOException {
        LoggingProperties properties = new LoggingProperties();
        RequestLoggingFilter filter = new RequestLoggingFilter(properties, new LogSanitizer(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/records");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> httpMethod = new AtomicReference<>();
        AtomicReference<String> httpPath = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            requestId.set(MDC.get(StructuredLogFields.REQUEST_ID));
            httpMethod.set(MDC.get(StructuredLogFields.HTTP_METHOD));
            httpPath.set(MDC.get(StructuredLogFields.HTTP_PATH));
        });

        assertThat(requestId.get()).isNotBlank();
        assertThat(response.getHeader(properties.getRequestIdHeader())).isEqualTo(requestId.get());
        assertThat(httpMethod).hasValue("GET");
        assertThat(httpPath).hasValue("/records");
    }

    @Test
    void shouldPreserveIncomingRequestId() throws ServletException, IOException {
        LoggingProperties properties = new LoggingProperties();
        RequestLoggingFilter filter = new RequestLoggingFilter(properties, new LogSanitizer(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/records/search");
        request.addHeader(properties.getRequestIdHeader(), "req-keep");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestId = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> requestId.set(MDC.get(StructuredLogFields.REQUEST_ID)));

        assertThat(requestId).hasValue("req-keep");
        assertThat(response.getHeader(properties.getRequestIdHeader())).isEqualTo("req-keep");
    }
}
