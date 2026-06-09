package com.callcenter.iam.interfaces.rest.support;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.callcenter.iam.application.auth.AuthenticationFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldLogStructuredUnauthorizedError() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "req-iam-1");

        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();

            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnauthorized(new AuthenticationFailedException("bad credentials"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isEqualTo(ApiResponse.failure("UNAUTHORIZED", "bad credentials"));
            assertThat(appender.list)
                    .hasSize(1)
                    .first()
                    .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                    .containsExactly(Level.WARN, "event=iam_request_failed requestId=req-iam-1 error=AuthenticationFailedException message=bad credentials");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
