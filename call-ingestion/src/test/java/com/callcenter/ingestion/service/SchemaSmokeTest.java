package com.callcenter.ingestion.service;

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

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
    }
}
