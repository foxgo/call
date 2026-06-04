CREATE TABLE tenant (
    id BIGINT NOT NULL,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    package_id BIGINT NULL,
    expire_time DATETIME NULL,
    quota INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code),
    KEY idx_status_expire_time (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE department (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_parent_name (tenant_id, parent_id, name),
    KEY idx_tenant_parent_id (tenant_id, parent_id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE department_closure (
    tenant_id BIGINT NOT NULL,
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, ancestor_id, descendant_id),
    UNIQUE KEY uk_ancestor_descendant (tenant_id, ancestor_id, descendant_id),
    KEY idx_tenant_ancestor_depth (tenant_id, ancestor_id, depth),
    KEY idx_tenant_descendant_depth (tenant_id, descendant_id, depth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE iam_user (
    id BIGINT NOT NULL,
    tenant_id BIGINT NULL,
    user_type VARCHAR(32) NOT NULL,
    username VARCHAR(64) NOT NULL,
    mobile VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(128) NULL,
    avatar VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL,
    last_login_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    UNIQUE KEY uk_tenant_mobile (tenant_id, mobile),
    UNIQUE KEY uk_tenant_email (tenant_id, email),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_tenant_last_login_time (tenant_id, last_login_time),
    KEY idx_user_type (user_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role (
    id BIGINT NOT NULL,
    tenant_id BIGINT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    built_in TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_code (tenant_id, role_code),
    KEY idx_tenant_role_type (tenant_id, role_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permission (
    id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NULL,
    action VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_role (
    tenant_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_tenant (tenant_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_permission (
    tenant_id BIGINT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_role_permission_tenant (tenant_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_department (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, department_id),
    UNIQUE KEY uk_user_department (user_id, department_id),
    KEY idx_user_department_tenant_dept (tenant_id, department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_data_scope (
    id BIGINT NOT NULL,
    tenant_id BIGINT NULL,
    role_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    department_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_scope_department (role_id, scope_type, department_id),
    KEY idx_role_data_scope_tenant_role (tenant_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id BIGINT NOT NULL,
    tenant_id BIGINT NULL,
    operator_id BIGINT NULL,
    operator_name VARCHAR(128) NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NULL,
    request_id VARCHAR(128) NULL,
    detail_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tenant_created_at (tenant_id, created_at),
    KEY idx_operator_created_at (operator_id, created_at),
    KEY idx_resource_type_resource_id (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Production note:
-- audit_log should use monthly RANGE partitioning on created_at when the runtime MySQL environment supports operational partition management.
