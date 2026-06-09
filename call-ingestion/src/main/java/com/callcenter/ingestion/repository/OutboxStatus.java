package com.callcenter.ingestion.repository;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    FAILED
}
