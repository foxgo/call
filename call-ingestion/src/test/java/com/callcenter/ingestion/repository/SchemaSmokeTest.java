package com.callcenter.ingestion.repository;

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
class SchemaSmokeTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("call_schema")
            .withUsername("call")
            .withPassword("call123");

    @Test
    void shouldCreateOutboxTableWithExpectedIndexes() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__create_call_event_outbox.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__update_call_event_outbox_indexes.sql"));
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(indexExists(jdbcTemplate, "call_event_outbox", "uk_call_event_outbox_event_id")).isTrue();
        assertThat(indexExists(jdbcTemplate, "call_event_outbox", "idx_call_event_outbox_status_next_attempt")).isFalse();
        assertThat(indexExists(jdbcTemplate, "call_event_outbox", "idx_call_event_outbox_publishable")).isTrue();
        assertThat(indexExists(jdbcTemplate, "call_event_outbox", "idx_call_event_outbox_processing")).isTrue();
        assertThat(indexExists(jdbcTemplate, "call_event_outbox", "idx_call_event_outbox_partition_key")).isTrue();
        assertThat(indexColumns(jdbcTemplate, "call_event_outbox", "idx_call_event_outbox_publishable"))
                .containsExactly("status", "next_attempt_at", "created_at", "id");
        assertThat(indexColumns(jdbcTemplate, "call_event_outbox", "idx_call_event_outbox_processing"))
                .containsExactly("status", "updated_at", "id");
    }

    @Test
    void shouldCreateAnalysisResultTableWithUniqueTenantCallIndex() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V4__create_call_analysis_result.sql"));
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(indexExists(jdbcTemplate, "call_analysis_result", "uk_call_analysis_result_tenant_call")).isTrue();
        assertThat(indexColumns(jdbcTemplate, "call_analysis_result", "uk_call_analysis_result_tenant_call"))
                .containsExactly("tenant_id", "call_id");
    }

    @Test
    void shouldAlterCallRecordTablesWithMetadataColumns() throws Exception {
        DataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE call_record_202605_00 (
                    call_id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    task_id BIGINT,
                    phone VARCHAR(20) NOT NULL,
                    line_number VARCHAR(20),
                    call_status TINYINT,
                    duration INT,
                    round_total INT,
                    start_time DATETIME,
                    end_time DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__alter_call_record_add_metadata.sql"));
        }

        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "recording_url")).isTrue();
        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "error_code")).isTrue();
        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "error_description")).isTrue();
        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "hangup_by")).isTrue();
        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "connected")).isTrue();
        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "ring_duration")).isTrue();
        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "ring_start_time")).isTrue();
        assertThat(columnExists(jdbcTemplate, "call_record_202605_00", "hangup_time")).isTrue();
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

    private static boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
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
