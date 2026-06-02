ALTER TABLE call_task
    ADD COLUMN caller_id_mode VARCHAR(32) NOT NULL DEFAULT 'HYBRID' AFTER max_concurrency,
    ADD COLUMN optimization_goal VARCHAR(32) NOT NULL DEFAULT 'ANSWER' AFTER caller_id_mode,
    ADD COLUMN answer_weight DOUBLE NOT NULL DEFAULT 1 AFTER optimization_goal,
    ADD COLUMN conversion_weight DOUBLE NOT NULL DEFAULT 0 AFTER answer_weight,
    ADD COLUMN cost_weight DOUBLE NOT NULL DEFAULT 0 AFTER conversion_weight,
    ADD COLUMN risk_weight DOUBLE NOT NULL DEFAULT 0 AFTER cost_weight,
    ADD COLUMN local_presence_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER risk_weight,
    ADD COLUMN same_caller_cooldown_seconds INT NOT NULL DEFAULT 3600 AFTER local_presence_enabled,
    ADD COLUMN max_caller_exposure_per_hour INT NOT NULL DEFAULT 200 AFTER same_caller_cooldown_seconds;

ALTER TABLE call_dial_unit_00
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_00_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_00_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_01
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_01_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_01_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_02
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_02_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_02_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_03
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_03_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_03_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_04
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_04_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_04_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_05
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_05_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_05_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_06
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_06_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_06_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_07
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_07_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_07_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_08
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_08_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_08_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_09
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_09_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_09_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_10
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_10_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_10_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_11
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_11_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_11_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_12
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_12_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_12_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_13
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_13_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_13_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_14
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_14_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_14_task_attempt_stage (task_id, attempt_stage);

ALTER TABLE call_dial_unit_15
    ADD COLUMN selected_caller_id BIGINT NULL AFTER dispatch_token,
    ADD COLUMN caller_id_selection_score DOUBLE NULL AFTER selected_caller_id,
    ADD COLUMN caller_id_selection_reason VARCHAR(255) NULL AFTER caller_id_selection_score,
    ADD COLUMN attempt_stage VARCHAR(32) NULL AFTER caller_id_selection_reason,
    ADD COLUMN ring_duration_seconds INT NULL AFTER attempt_stage,
    ADD COLUMN talk_duration_seconds INT NULL AFTER ring_duration_seconds,
    ADD COLUMN hangup_code VARCHAR(64) NULL AFTER talk_duration_seconds,
    ADD KEY idx_call_dial_unit_15_task_selected_caller (task_id, selected_caller_id),
    ADD KEY idx_call_dial_unit_15_task_attempt_stage (task_id, attempt_stage);

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
