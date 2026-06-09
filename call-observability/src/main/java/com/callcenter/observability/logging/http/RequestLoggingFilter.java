package com.callcenter.observability.logging.http;

import com.callcenter.observability.logging.LogSanitizer;
import com.callcenter.observability.logging.MdcUtils;
import com.callcenter.observability.logging.StructuredLogFields;
import com.callcenter.observability.logging.config.LoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final LoggingProperties properties;
    private final LogSanitizer sanitizer;

    public RequestLoggingFilter(LoggingProperties properties, LogSanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Map<String, String> previous = MdcUtils.copy();
        long start = System.currentTimeMillis();
        String requestId = resolveRequestId(request);
        try {
            response.setHeader(properties.getRequestIdHeader(), requestId);
            MdcUtils.put(StructuredLogFields.REQUEST_ID, requestId);
            MdcUtils.put(StructuredLogFields.HTTP_METHOD, request.getMethod());
            MdcUtils.put(StructuredLogFields.HTTP_PATH, request.getRequestURI());
            MdcUtils.put(StructuredLogFields.CLIENT_IP, request.getRemoteAddr());
            filterChain.doFilter(request, response);
        } finally {
            log.info(
                    "event=http_request_completed requestId={} method={} path={} status={} costMs={}",
                    requestId,
                    request.getMethod(),
                    sanitizer.sanitizeText(request.getRequestURI()),
                    response.getStatus(),
                    System.currentTimeMillis() - start
            );
            MdcUtils.restore(previous);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(properties.getRequestIdHeader());
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
