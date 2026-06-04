INSERT INTO permission (id, permission_code, permission_name, resource_type, action)
VALUES
    (101, 'iam:user:create', 'Create User', 'USER', 'CREATE'),
    (102, 'iam:user:update', 'Update User', 'USER', 'UPDATE'),
    (103, 'iam:user:delete', 'Delete User', 'USER', 'DELETE'),
    (201, 'iam:role:update', 'Update Role', 'ROLE', 'UPDATE'),
    (202, 'iam:role:scope', 'Update Role Scope', 'ROLE', 'SCOPE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        resource_type = VALUES(resource_type),
                        action = VALUES(action);

INSERT INTO role (id, tenant_id, role_code, role_name, role_type, built_in, status)
VALUES
    (1, NULL, 'TENANT_ADMIN', 'Tenant Admin', 'TENANT_SYSTEM', 1, 'ACTIVE'),
    (2, NULL, 'SUPERVISOR', 'Supervisor', 'TENANT_SYSTEM', 1, 'ACTIVE'),
    (3, NULL, 'OPERATOR', 'Operator', 'TENANT_SYSTEM', 1, 'ACTIVE'),
    (4, NULL, 'QA', 'QA', 'TENANT_SYSTEM', 1, 'ACTIVE')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name),
                        role_type = VALUES(role_type),
                        built_in = VALUES(built_in),
                        status = VALUES(status);
