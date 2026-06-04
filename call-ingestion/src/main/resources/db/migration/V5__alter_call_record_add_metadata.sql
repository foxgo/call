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
