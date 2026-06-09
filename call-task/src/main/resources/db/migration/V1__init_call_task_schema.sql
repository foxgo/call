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
    priority INT NOT NULL DEFAULT 4,
    max_concurrency INT NOT NULL,
    caller_id_mode VARCHAR(32) NOT NULL DEFAULT 'HYBRID',
    optimization_goal VARCHAR(32) NOT NULL DEFAULT 'ANSWER',
    answer_weight DOUBLE NOT NULL DEFAULT 1,
    conversion_weight DOUBLE NOT NULL DEFAULT 0,
    cost_weight DOUBLE NOT NULL DEFAULT 0,
    risk_weight DOUBLE NOT NULL DEFAULT 0,
    local_presence_enabled TINYINT(1) NOT NULL DEFAULT 0,
    same_caller_cooldown_seconds INT NOT NULL DEFAULT 3600,
    max_caller_exposure_per_hour INT NOT NULL DEFAULT 200,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    next_dispatch_time DATETIME NULL COMMENT 'Deprecated. Scheduler no longer reads this column.',
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
    call_id BIGINT NOT NULL PRIMARY KEY,
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
    selected_caller_id BIGINT NULL,
    caller_id_selection_score DOUBLE NULL,
    caller_id_selection_reason VARCHAR(255) NULL,
    attempt_stage VARCHAR(32) NULL,
    ring_duration_seconds INT NULL,
    talk_duration_seconds INT NULL,
    hangup_code VARCHAR(64) NULL,
    inflight_expire_at DATETIME NULL,
    biz_idempotency_key VARCHAR(128) NOT NULL DEFAULT '',
    failure_code VARCHAR(64) NULL,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_dial_unit_00_task_phone_biz (task_id, phone, biz_idempotency_key),
    KEY idx_call_dial_unit_00_task_status_next_call (task_id, status, next_call_time, call_id),
    KEY idx_call_dial_unit_00_task_dispatch_token (task_id, dispatch_token),
    KEY idx_call_dial_unit_00_task_selected_caller (task_id, selected_caller_id),
    KEY idx_call_dial_unit_00_task_attempt_stage (task_id, attempt_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$

CREATE PROCEDURE init_call_dial_unit_tables()
BEGIN
  DECLARE idx INT DEFAULT 1;
  DECLARE suffix_value VARCHAR(2);

  WHILE idx < 16 DO
    SET suffix_value = LPAD(idx, 2, '0');

    SET @create_sql = CONCAT(
      'CREATE TABLE IF NOT EXISTS call_dial_unit_', suffix_value, ' LIKE call_dial_unit_00'
    );

    PREPARE stmt FROM @create_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET @alter_sql = CONCAT(
      'ALTER TABLE call_dial_unit_', suffix_value, ' ',
      'DROP INDEX uk_call_dial_unit_00_task_phone_biz, ',
      'DROP INDEX idx_call_dial_unit_00_task_status_next_call, ',
      'DROP INDEX idx_call_dial_unit_00_task_dispatch_token, ',
      'DROP INDEX idx_call_dial_unit_00_task_selected_caller, ',
      'DROP INDEX idx_call_dial_unit_00_task_attempt_stage, ',
      'ADD UNIQUE KEY uk_call_dial_unit_', suffix_value, '_task_phone_biz (task_id, phone, biz_idempotency_key), ',
      'ADD KEY idx_call_dial_unit_', suffix_value, '_task_status_next_call (task_id, status, next_call_time, call_id), ',
      'ADD KEY idx_call_dial_unit_', suffix_value, '_task_dispatch_token (task_id, dispatch_token), ',
      'ADD KEY idx_call_dial_unit_', suffix_value, '_task_selected_caller (task_id, selected_caller_id), ',
      'ADD KEY idx_call_dial_unit_', suffix_value, '_task_attempt_stage (task_id, attempt_stage)'
    );

    PREPARE stmt FROM @alter_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET idx = idx + 1;
  END WHILE;
END$$

DELIMITER ;

CALL init_call_dial_unit_tables();

DROP PROCEDURE init_call_dial_unit_tables;

CREATE TABLE IF NOT EXISTS call_caller_id (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    caller_id VARCHAR(32) NOT NULL,
    pool_type VARCHAR(32) NOT NULL,
    carrier VARCHAR(32) NULL,
    province_code VARCHAR(32) NULL,
    city_code VARCHAR(32) NULL,
    cost_score DOUBLE NOT NULL DEFAULT 0,
    trust_score DOUBLE NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    cooldown_until DATETIME NULL,
    last_used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_caller_id_tenant_caller (tenant_id, caller_id),
    KEY idx_call_caller_id_tenant_status_pool (tenant_id, status, pool_type),
    KEY idx_call_caller_id_tenant_cooldown (tenant_id, cooldown_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS call_task_caller_id_binding (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    caller_id_id BIGINT NOT NULL,
    binding_type VARCHAR(32) NOT NULL,
    priority_boost INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_task_caller_id_binding (task_id, caller_id_id, binding_type),
    KEY idx_call_task_caller_id_binding_task (tenant_id, task_id, binding_type),
    KEY idx_call_task_caller_id_binding_caller (tenant_id, caller_id_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS call_caller_id_stats (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    caller_id_id BIGINT NOT NULL,
    attempt_stage VARCHAR(32) NOT NULL,
    time_bucket DATETIME NOT NULL,
    attempt_count BIGINT NOT NULL DEFAULT 0,
    ring_count BIGINT NOT NULL DEFAULT 0,
    answer_count BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0,
    total_talk_seconds BIGINT NOT NULL DEFAULT 0,
    failure_code_summary VARCHAR(1024) NULL,
    health_score DOUBLE NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_caller_id_stats_bucket (tenant_id, caller_id_id, attempt_stage, time_bucket),
    KEY idx_call_caller_id_stats_lookup (tenant_id, caller_id_id, attempt_stage),
    KEY idx_call_caller_id_stats_bucket_time (tenant_id, time_bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
