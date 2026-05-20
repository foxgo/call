package com.callcenter.ingestion.model;

public record InboundMessage<T>(
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String messageKey,
        MessageType messageType,
        String idempotencyKey,
        T payload,
        int attempt,
        long firstFailureAt
) {

    public static <T> InboundMessage<T> main(
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String messageKey,
            MessageType messageType,
            String idempotencyKey,
            T payload
    ) {
        return new InboundMessage<>(
                sourceTopic,
                sourcePartition,
                sourceOffset,
                messageKey,
                messageType,
                idempotencyKey,
                payload,
                0,
                0L
        );
    }

    public static <T> InboundMessage<T> retry(
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            String messageKey,
            MessageType messageType,
            String idempotencyKey,
            T payload,
            int attempt,
            long firstFailureAt
    ) {
        return new InboundMessage<>(
                sourceTopic,
                sourcePartition,
                sourceOffset,
                messageKey,
                messageType,
                idempotencyKey,
                payload,
                attempt,
                firstFailureAt
        );
    }
}
