package com.callcenter.search.model;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long total, String source) {

    public static <T> PageResponse<T> empty(int page, int size, String source) {
        return new PageResponse<>(List.of(), page, size, 0L, source);
    }
}
