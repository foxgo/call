package com.callcenter.observability.logging;

import com.callcenter.observability.logging.config.LoggingProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class LogSanitizer {

    private static final String MASK = "******";
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|token|authorization|cookie)=([^\\s,&]+)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1\\d{10})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\w)(\\d{17}[\\dXx])(?!\\w)");

    private final LoggingProperties properties;

    public LogSanitizer(LoggingProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> sanitizeMap(Map<String, Object> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> sanitized.put(key, sanitizeValue(key, value)));
        return sanitized;
    }

    public Object sanitizeValue(String fieldName, Object value) {
        if (!properties.getMasking().isEnabled() || value == null) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> nested.put(String.valueOf(key), sanitizeValue(String.valueOf(key), nestedValue)));
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(sanitizeValue(fieldName, item));
            }
            return items;
        }
        if (value instanceof CharSequence sequence) {
            return sanitizeString(fieldName, sequence.toString());
        }
        return value;
    }

    public String sanitizeText(String text) {
        if (text == null || !properties.getMasking().isEnabled()) {
            return text;
        }
        String sanitized = BEARER_PATTERN.matcher(text).replaceAll("Bearer " + MASK);
        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll(matchResult -> matchResult.group(1) + "=" + MASK);
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll(matchResult -> maskPhone(matchResult.group(1)));
        return ID_CARD_PATTERN.matcher(sanitized).replaceAll(matchResult -> maskIdCard(matchResult.group(1)));
    }

    private String sanitizeString(String fieldName, String value) {
        if (matches(fieldName, properties.getMasking().getSecretFields())) {
            return MASK;
        }
        if (matches(fieldName, properties.getMasking().getPhoneFields())) {
            return maskPhone(value);
        }
        if (matches(fieldName, properties.getMasking().getIdCardFields())) {
            return maskIdCard(value);
        }
        if (matches(fieldName, properties.getMasking().getNameFields())) {
            return maskName(value);
        }
        return sanitizeText(value);
    }

    private boolean matches(String fieldName, List<String> candidates) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private String maskPhone(String value) {
        if (value == null || value.length() < 7) {
            return MASK;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskIdCard(String value) {
        if (value == null || value.length() < 5) {
            return MASK;
        }
        return value.substring(0, 3) + "*".repeat(Math.max(0, value.length() - 5)) + value.substring(value.length() - 2);
    }

    private String maskName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1) + "*".repeat(Math.max(0, value.length() - 1));
    }
}
