package com.callcenter.ingestion.infrastructure.outbox.persistence;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    FAILED
}
