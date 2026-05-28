ALTER TABLE call_task
    MODIFY COLUMN next_dispatch_time DATETIME NULL COMMENT 'Deprecated. Scheduler no longer reads this column.';
