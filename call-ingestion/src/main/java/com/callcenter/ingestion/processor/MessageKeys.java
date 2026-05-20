package com.callcenter.ingestion.processor;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;

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

    public static String recordDocumentId(CallRecordEntity entity) {
        return String.valueOf(entity.getCallId());
    }

    public static String roundDocumentId(CallRoundEntity entity) {
        return entity.getCallId() + ":" + entity.getRoundId();
    }

    public static String roundDocumentId(CallRoundMessage message) {
        return message.callId() + ":" + message.roundId();
    }
}
