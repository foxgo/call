CREATE TABLE IF NOT EXISTS call_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    partition_key VARCHAR(64) NOT NULL,
    schema_version INT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NULL,
    last_error TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_event_outbox_event_id (event_id),
    KEY idx_call_event_outbox_publishable (status, next_attempt_at, created_at, id),
    KEY idx_call_event_outbox_processing (status, updated_at, id),
    KEY idx_call_event_outbox_partition_key (partition_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS call_dead_letter_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_key VARCHAR(255) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    source_topic VARCHAR(128) NOT NULL,
    source_partition INT NOT NULL,
    source_offset BIGINT NOT NULL,
    dlq_topic VARCHAR(128) NOT NULL,
    dlq_queue_offset BIGINT NOT NULL,
    origin_message_id VARCHAR(128) NULL,
    message_key VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_type VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    dlq_attempt INT NOT NULL,
    dlq_max_attempts INT NOT NULL,
    first_failure_at DATETIME NULL,
    last_failure_at DATETIME NOT NULL,
    error_class VARCHAR(255) NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_dead_letter_task_task_key (task_key),
    KEY idx_call_dead_letter_task_status_created (status, created_at, id),
    KEY idx_call_dead_letter_task_message_type (message_type, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS call_analysis_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    call_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    tags JSON NOT NULL,
    risk_flag TINYINT(1) NULL,
    quality_score FLOAT NULL,
    ai_version VARCHAR(64) NULL,
    error_message TEXT NULL,
    completed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_call_analysis_result_tenant_call (tenant_id, call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$

CREATE PROCEDURE alter_call_record_tables()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE table_name_value VARCHAR(128);
  DECLARE cursor_tables CURSOR FOR
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name LIKE 'call_record\\_%';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cursor_tables;

  read_loop: LOOP
    FETCH cursor_tables INTO table_name_value;
    IF done = 1 THEN
      LEAVE read_loop;
    END IF;

    SET @alter_sql = CONCAT(
      'ALTER TABLE `', table_name_value, '` ',
      'ADD COLUMN IF NOT EXISTS `recording_url` VARCHAR(512) NULL AFTER `round_total`, ',
      'ADD COLUMN IF NOT EXISTS `error_code` INT NULL AFTER `recording_url`, ',
      'ADD COLUMN IF NOT EXISTS `error_description` TEXT NULL AFTER `error_code`, ',
      'ADD COLUMN IF NOT EXISTS `hangup_by` TINYINT NULL AFTER `error_description`, ',
      'ADD COLUMN IF NOT EXISTS `connected` TINYINT NULL AFTER `hangup_by`, ',
      'ADD COLUMN IF NOT EXISTS `ring_duration` BIGINT NULL AFTER `connected`, ',
      'ADD COLUMN IF NOT EXISTS `ring_start_time` DATETIME(3) NULL AFTER `ring_duration`, ',
      'ADD COLUMN IF NOT EXISTS `hangup_time` DATETIME(3) NULL AFTER `ring_start_time`'
    );

    PREPARE stmt FROM @alter_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;

  CLOSE cursor_tables;
END$$

DELIMITER ;

CALL alter_call_record_tables();

DROP PROCEDURE alter_call_record_tables;
