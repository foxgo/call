CREATE TABLE IF NOT EXISTS call_task (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    queued_count INT NOT NULL DEFAULT 0,
    dialing_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    max_concurrency INT NOT NULL,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    next_dispatch_time DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_call_task_tenant_status (tenant_id, status),
    KEY idx_call_task_tenant_updated (tenant_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS call_task_import_batch (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_call_task_import_batch_task_created (task_id, created_at),
    KEY idx_call_task_import_batch_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS call_dial_unit_00 (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    import_batch_id BIGINT NOT NULL,
    phone VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    score FLOAT NOT NULL DEFAULT 0,
    last_call_time DATETIME NULL,
    next_call_time DATETIME NULL,
    dispatch_token VARCHAR(64) NULL,
    inflight_expire_at DATETIME NULL,
    biz_idempotency_key VARCHAR(128) NOT NULL DEFAULT '',
    failure_code VARCHAR(64) NULL,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_dial_unit_00_task_phone_biz (task_id, phone, biz_idempotency_key),
    KEY idx_call_dial_unit_00_task_status_next_call (task_id, status, next_call_time, id),
    KEY idx_call_dial_unit_00_task_dispatch_token (task_id, dispatch_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS call_dial_unit_01 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_01
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_01_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_01_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_01_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_02 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_02
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_02_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_02_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_02_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_03 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_03
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_03_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_03_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_03_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_04 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_04
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_04_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_04_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_04_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_05 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_05
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_05_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_05_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_05_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_06 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_06
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_06_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_06_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_06_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_07 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_07
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_07_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_07_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_07_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_08 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_08
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_08_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_08_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_08_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_09 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_09
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_09_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_09_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_09_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_10 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_10
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_10_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_10_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_10_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_11 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_11
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_11_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_11_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_11_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_12 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_12
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_12_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_12_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_12_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_13 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_13
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_13_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_13_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_13_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_14 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_14
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_14_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_14_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_14_task_dispatch_token (task_id, dispatch_token);

CREATE TABLE IF NOT EXISTS call_dial_unit_15 LIKE call_dial_unit_00;
ALTER TABLE call_dial_unit_15
    DROP INDEX uk_call_dial_unit_00_task_phone_biz,
    DROP INDEX idx_call_dial_unit_00_task_status_next_call,
    DROP INDEX idx_call_dial_unit_00_task_dispatch_token,
    ADD UNIQUE KEY uk_call_dial_unit_15_task_phone_biz (task_id, phone, biz_idempotency_key),
    ADD KEY idx_call_dial_unit_15_task_status_next_call (task_id, status, next_call_time, id),
    ADD KEY idx_call_dial_unit_15_task_dispatch_token (task_id, dispatch_token);
