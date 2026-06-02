package com.callcenter.task.db;

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
class TaskSchemaMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("call_task_schema")
            .withUsername("call")
            .withPassword("call123");

    @Test
    void shouldCreateTaskTablesAndDialUnitShards() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__create_call_task_tables.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__deprecate_call_task_next_dispatch_time.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__add_caller_id_selection_schema.sql"));
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(tableExists(jdbcTemplate, "call_task")).isTrue();
        assertThat(tableExists(jdbcTemplate, "call_task_import_batch")).isTrue();
        assertThat(tableExists(jdbcTemplate, "call_dial_unit_00")).isTrue();
        assertThat(tableExists(jdbcTemplate, "call_dial_unit_15")).isTrue();
        assertThat(tableExists(jdbcTemplate, "call_caller_id")).isTrue();
        assertThat(tableExists(jdbcTemplate, "call_task_caller_id_binding")).isTrue();
        assertThat(tableExists(jdbcTemplate, "call_caller_id_stats")).isTrue();
        assertThat(columns(jdbcTemplate, "call_task"))
                .contains("caller_id_mode", "optimization_goal", "answer_weight", "max_caller_exposure_per_hour");
        assertThat(columns(jdbcTemplate, "call_dial_unit_00"))
                .contains("selected_caller_id", "caller_id_selection_score", "attempt_stage", "talk_duration_seconds");
        assertThat(indexExists(jdbcTemplate, "call_dial_unit_00", "uk_call_dial_unit_00_task_phone_biz")).isTrue();
        assertThat(indexColumns(jdbcTemplate, "call_dial_unit_00", "idx_call_dial_unit_00_task_status_next_call"))
                .containsExactly("task_id", "status", "next_call_time", "id");
        assertThat(indexExists(jdbcTemplate, "call_caller_id", "uk_call_caller_id_tenant_caller")).isTrue();
        assertThat(indexExists(jdbcTemplate, "call_caller_id_stats", "uk_call_caller_id_stats_bucket")).isTrue();
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private static boolean indexExists(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                Integer.class,
                tableName,
                indexName
        );
        return count != null && count > 0;
    }

    private static List<String> indexColumns(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        return jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                ORDER BY seq_in_index
                """,
                String.class,
                tableName,
                indexName
        );
    }

    private static List<String> columns(JdbcTemplate jdbcTemplate, String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                ORDER BY ordinal_position
                """,
                String.class,
                tableName
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
