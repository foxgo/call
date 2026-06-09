package com.callcenter.ingestion.model;

import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRoundData;
import com.callcenter.ingestion.model.CallRecordMessage;
import com.callcenter.ingestion.model.CallRoundMessage;

public final class MessageKeys {

    private MessageKeys() {
    }

    public static String recordIdempotencyKey(CallRecordMessage message) {
        if (message.callId() == null) {
            throw new IllegalArgumentException("callId is required");
        }
        return String.valueOf(message.callId());
    }

    public static String roundIdempotencyKey(CallRoundMessage message) {
        if (message.callId() <= 0) {
            throw new IllegalArgumentException("callId is required");
        }
        if (message.roundId() == null) {
            throw new IllegalArgumentException("roundId is required");
        }
        return roundDocumentId(message);
    }

    public static String recordDocumentId(long callId) {
        return String.valueOf(callId);
    }

    public static String roundDocumentId(long callId, long roundId) {
        return callId + ":" + roundId;
    }

    public static String recordDocumentId(CallRecordData entity) {
        return recordDocumentId(entity.callId());
    }

    public static String roundDocumentId(CallRoundData entity) {
        return roundDocumentId(entity.callId(), entity.roundId());
    }

    public static String roundDocumentId(CallRoundMessage message) {
        return roundDocumentId(message.callId(), message.roundId());
    }

    public static String domainEventIdempotencyKey(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        return eventId;
    }
}
