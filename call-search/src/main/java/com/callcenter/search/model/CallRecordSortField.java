package com.callcenter.search.model;

import java.util.Arrays;

public enum CallRecordSortField {
    START_TIME("start_time"),
    DURATION("duration"),
    CREATED_AT("created_at");

    private final String fieldName;

    CallRecordSortField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }

    public static CallRecordSortField from(String value) {
        if (value == null || value.isBlank()) {
            return START_TIME;
        }
        return Arrays.stream(values())
                .filter(field -> field.name().equalsIgnoreCase(value) || field.fieldName.equalsIgnoreCase(value))
                .findFirst()
                .orElse(START_TIME);
    }
}

