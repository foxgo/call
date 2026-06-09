package com.callcenter.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.callcenter.observability.logging.config.LoggingProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    private final LogSanitizer sanitizer = new LogSanitizer(new LoggingProperties());

    @Test
    void shouldFullyMaskSecretFields() {
        Map<String, Object> sanitized = sanitizer.sanitizeMap(Map.of(
                "password", "s3cr3t",
                "token", "abc",
                "authorization", "Bearer xyz"
        ));

        assertThat(sanitized)
                .containsEntry("password", "******")
                .containsEntry("token", "******")
                .containsEntry("authorization", "******");
    }

    @Test
    void shouldPartiallyMaskPhoneAndIdCardValues() {
        Map<String, Object> sanitized = sanitizer.sanitizeMap(Map.of(
                "phone", "13812345678",
                "idCard", "110101199003071234"
        ));

        assertThat(sanitized)
                .containsEntry("phone", "138****5678")
                .containsEntry("idCard", "110*************34");
    }

    @Test
    void shouldMaskNestedCollectionsRecursively() {
        Map<String, Object> sanitized = sanitizer.sanitizeMap(Map.of(
                "payload", Map.of(
                        "mobile", "13987654321",
                        "children", List.of(
                                Map.of("refreshToken", "refresh-1"),
                                Map.of("contactPhone", "13700001111")
                        )
                )
        ));

        assertThat(sanitized)
                .containsEntry("payload", Map.of(
                        "mobile", "139****4321",
                        "children", List.of(
                                Map.of("refreshToken", "******"),
                                Map.of("contactPhone", "137****1111")
                        )
                ));
    }

    @Test
    void shouldMaskSensitivePatternsInRawText() {
        String sanitized = sanitizer.sanitizeText(
                "Authorization=Bearer abc.def token=secret password=123456 phone=13812345678"
        );

        assertThat(sanitized)
                .doesNotContain("abc.def")
                .doesNotContain("secret")
                .doesNotContain("123456")
                .contains("138****5678");
    }
}
