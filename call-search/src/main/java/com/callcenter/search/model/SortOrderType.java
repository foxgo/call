package com.callcenter.search.model;

public enum SortOrderType {
    ASC,
    DESC;

    public static SortOrderType from(String value) {
        if (value == null || value.isBlank()) {
            return DESC;
        }
        return SortOrderType.valueOf(value.trim().toUpperCase());
    }
}

