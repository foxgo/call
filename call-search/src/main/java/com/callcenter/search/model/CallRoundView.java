package com.callcenter.search.model;

public record CallRoundView(
        Integer roundIndex,
        String speaker,
        String content,
        String intent,
        String startTime
) {
}

