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
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permission", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role", Integer.class)).isEqualTo(4);
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
