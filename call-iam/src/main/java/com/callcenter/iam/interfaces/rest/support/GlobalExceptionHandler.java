package com.callcenter.iam.interfaces.rest.support;

import com.callcenter.iam.application.auth.AuthenticationFailedException;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "validation failed" : fieldError.getDefaultMessage();
        logWarn(ex, message);
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException ex) {
        logWarn(ex, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DomainRuleViolationException ex) {
        logWarn(ex, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("DOMAIN_RULE_VIOLATION", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        logWarn(ex, ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(AuthenticationFailedException ex) {
        logWarn(ex, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("UNAUTHORIZED", ex.getMessage()));
    }

    private void logWarn(Exception ex, String message) {
        log.warn(
                "event=iam_request_failed requestId={} error={} message={}",
                MDC.get("requestId"),
                ex.getClass().getSimpleName(),
                message
        );
    }
}
