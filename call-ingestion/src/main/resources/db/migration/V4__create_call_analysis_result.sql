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
