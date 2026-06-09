INSERT INTO tenant (id, tenant_code, tenant_name, status, package_id, expire_time, quota)
VALUES
    (1001, 'default', 'Default Tenant', 'ACTIVE', NULL, '2099-12-31 23:59:59', NULL)
ON DUPLICATE KEY UPDATE tenant_code = VALUES(tenant_code),
                        tenant_name = VALUES(tenant_name),
                        status = VALUES(status),
                        package_id = VALUES(package_id),
                        expire_time = VALUES(expire_time),
                        quota = VALUES(quota);

INSERT INTO role (id, tenant_id, role_code, role_name, role_type, built_in, status)
VALUES
    (1001, NULL, 'PLATFORM_ADMIN', 'Platform Admin', 'PLATFORM_SYSTEM', 1, 'ACTIVE')
ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id),
                        role_code = VALUES(role_code),
                        role_name = VALUES(role_name),
                        role_type = VALUES(role_type),
                        built_in = VALUES(built_in),
                        status = VALUES(status);

INSERT INTO iam_user (id, tenant_id, user_type, username, mobile, email, password_hash, nickname, status, last_login_time)
VALUES
    (1001, NULL, 'PLATFORM', 'platform-admin', '13900000001', 'platform-admin@callcenter.local', '$2y$10$JnlTGAcCnc7Lw6ZqjJXv3ukr3Ik3ks1.aeWEXlnlswARe7Dmj.JKy', 'Platform Admin', 'ENABLE', NULL),
    (1002, 1001, 'TENANT', 'tenant-admin', '13900000002', 'tenant-admin@callcenter.local', '$2y$10$JnlTGAcCnc7Lw6ZqjJXv3ukr3Ik3ks1.aeWEXlnlswARe7Dmj.JKy', 'Tenant Admin', 'ENABLE', NULL)
ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id),
                        user_type = VALUES(user_type),
                        username = VALUES(username),
                        mobile = VALUES(mobile),
                        email = VALUES(email),
                        password_hash = VALUES(password_hash),
                        nickname = VALUES(nickname),
                        status = VALUES(status),
                        last_login_time = VALUES(last_login_time);

INSERT INTO user_role (tenant_id, user_id, role_id)
VALUES
    (NULL, 1001, 1001),
    (1001, 1002, 1)
ON DUPLICATE KEY UPDATE tenant_id = VALUES(tenant_id);
