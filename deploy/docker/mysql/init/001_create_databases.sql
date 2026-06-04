CREATE DATABASE IF NOT EXISTS call_0 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS call_1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS call_2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS call_3 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'call'@'%' IDENTIFIED BY 'call123';
GRANT ALL PRIVILEGES ON call_0.* TO 'call'@'%';
GRANT ALL PRIVILEGES ON call_1.* TO 'call'@'%';
GRANT ALL PRIVILEGES ON call_2.* TO 'call'@'%';
GRANT ALL PRIVILEGES ON call_3.* TO 'call'@'%';
FLUSH PRIVILEGES;

DELIMITER $$

CREATE PROCEDURE init_month_tables(IN db_name VARCHAR(64), IN ym VARCHAR(6))
BEGIN
  DECLARE idx INT DEFAULT 0;
  DECLARE record_sql TEXT;
  DECLARE round_sql TEXT;

  WHILE idx < 16 DO
    SET @record_table = CONCAT(db_name, '.call_record_', ym, '_', LPAD(idx, 2, '0'));
    SET @round_table = CONCAT(db_name, '.call_round_', ym, '_', LPAD(idx, 2, '0'));

    SET record_sql = CONCAT(
      'CREATE TABLE IF NOT EXISTS ', @record_table, ' (',
      'call_id BIGINT PRIMARY KEY,',
      'tenant_id BIGINT NOT NULL,',
      'task_id BIGINT,',
      'phone VARCHAR(20) NOT NULL,',
      'line_number VARCHAR(20),',
      'call_status TINYINT,',
      'duration INT,',
      'round_total INT,',
      'recording_url VARCHAR(512),',
      'error_code INT,',
      'error_description TEXT,',
      'hangup_by TINYINT,',
      'connected TINYINT,',
      'ring_duration BIGINT,',
      'ring_start_time DATETIME(3),',
      'hangup_time DATETIME(3),',
      'start_time DATETIME,',
      'end_time DATETIME,',
      'created_at DATETIME DEFAULT CURRENT_TIMESTAMP,',
      'INDEX idx_tenant_time (tenant_id, start_time),',
      'INDEX idx_task (task_id)',
      ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4'
    );

    SET round_sql = CONCAT(
      'CREATE TABLE IF NOT EXISTS ', @round_table, ' (',
      'round_id BIGINT PRIMARY KEY,',
      'call_id BIGINT NOT NULL,',
      'tenant_id BIGINT NOT NULL,',
      'round_index INT,',
      'speaker VARCHAR(16),',
      'content TEXT,',
      'intent VARCHAR(64),',
      'start_time DATETIME,',
      'created_at DATETIME DEFAULT CURRENT_TIMESTAMP,',
      'INDEX idx_call (call_id)',
      ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4'
    );

    PREPARE stmt FROM record_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    PREPARE stmt FROM round_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;

    SET idx = idx + 1;
  END WHILE;
END$$

DELIMITER ;

CALL init_month_tables('call_0', '202605');
CALL init_month_tables('call_1', '202605');
CALL init_month_tables('call_2', '202605');
CALL init_month_tables('call_3', '202605');

DROP PROCEDURE init_month_tables;
