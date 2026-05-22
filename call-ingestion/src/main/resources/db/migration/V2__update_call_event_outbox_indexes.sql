ALTER TABLE call_event_outbox
    DROP INDEX idx_call_event_outbox_status_next_attempt,
    ADD KEY idx_call_event_outbox_publishable (status, next_attempt_at, created_at, id),
    ADD KEY idx_call_event_outbox_processing (status, updated_at, id);
