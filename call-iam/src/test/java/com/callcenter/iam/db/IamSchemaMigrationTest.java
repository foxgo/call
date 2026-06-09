package com.callcenter.iam.db;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class IamSchemaMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("call_iam_schema")
            .withUsername("call")
            .withPassword("call123");

    @Test
    void shouldCreateCoreIamTables() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_call_iam_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__seed_call_iam_admin_accounts.sql"));
        }

        assertThat(tables(new JdbcTemplate(dataSource))).contains(
                "tenant",
                "department",
                "department_closure",
                "iam_user",
                "role",
                "permission",
                "user_role",
                "role_permission",
                "user_department",
                "role_data_scope",
                "audit_log"
        );
    }

    @Test
    void shouldSeedBuiltInPermissionsAndRoles() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_call_iam_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__seed_call_iam_admin_accounts.sql"));
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permission", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role", Integer.class)).isEqualTo(5);
    }

    @Test
    void shouldSeedPlatformAndTenantAdminAccounts() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_call_iam_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__seed_call_iam_admin_accounts.sql"));
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Long defaultTenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM tenant WHERE tenant_code = 'default'",
                Long.class
        );
        assertThat(defaultTenantId).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_user WHERE tenant_id IS NULL AND username = 'platform-admin' AND user_type = 'PLATFORM'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_user WHERE tenant_id = ? AND username = 'tenant-admin' AND user_type = 'TENANT'",
                Integer.class,
                defaultTenantId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM iam_user WHERE tenant_id IS NULL AND username = 'platform-admin'",
                String.class
        )).startsWith("$2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM iam_user WHERE tenant_id = ? AND username = 'tenant-admin'",
                String.class,
                defaultTenantId
        )).startsWith("$2");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_role ur
                JOIN iam_user u ON u.id = ur.user_id
                JOIN role r ON r.id = ur.role_id
                WHERE u.username = 'platform-admin'
                  AND u.tenant_id IS NULL
                  AND r.role_code = 'PLATFORM_ADMIN'
                """,
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_role ur
                JOIN iam_user u ON u.id = ur.user_id
                JOIN role r ON r.id = ur.role_id
                WHERE u.username = 'tenant-admin'
                  AND u.tenant_id = ?
                  AND r.role_code = 'TENANT_ADMIN'
                """,
                Integer.class,
                defaultTenantId
        )).isEqualTo(1);
    }

    private static List<String> tables(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                ORDER BY table_name
                """,
                String.class
        );
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }
}
