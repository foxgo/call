package com.callcenter.task.config;

import com.callcenter.persistence.config.CallDatasourceProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class TaskFlywayMigrationStrategyTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("mysql")
            .withUsername("root")
            .withPassword("root123");

    @Test
    void shouldMigrateEveryConfiguredDatabaseEvenWhenSchemaIsNotEmpty() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            for (String database : List.of("call_0", "call_1", "call_2", "call_3")) {
                connection.createStatement().execute("CREATE DATABASE " + database);
                connection.createStatement().execute("""
                        CREATE TABLE %s.call_record_202605_00 (
                            call_id BIGINT PRIMARY KEY,
                            tenant_id BIGINT NOT NULL,
                            phone VARCHAR(20) NOT NULL,
                            round_total INT
                        )
                        """.formatted(database));
            }
        }

        CallDatasourceProperties properties = new CallDatasourceProperties();
        properties.setNodes(List.of(
                node(0, "call_0"),
                node(1, "call_1"),
                node(2, "call_2"),
                node(3, "call_3")
        ));

        FlywayMigrationStrategy strategy = new CallTaskFlywayConfig().flywayMigrationStrategy(properties);
        strategy.migrate(null);

        for (String database : List.of("call_0", "call_1", "call_2", "call_3")) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource(database));
            assertThat(tableExists(jdbcTemplate, "flyway_schema_history_call_task")).isTrue();
            assertThat(tableExists(jdbcTemplate, "flyway_schema_history")).isFalse();
            assertThat(tableExists(jdbcTemplate, "call_task")).isTrue();
            assertThat(tableExists(jdbcTemplate, "call_task_import_batch")).isTrue();
            assertThat(tableExists(jdbcTemplate, "call_dial_unit_15")).isTrue();
            assertThat(tableExists(jdbcTemplate, "call_caller_id")).isTrue();
            assertThat(tableExists(jdbcTemplate, "call_caller_id_stats")).isTrue();
        }
    }

    private static CallDatasourceProperties.Node node(int index, String database) {
        CallDatasourceProperties.Node node = new CallDatasourceProperties.Node();
        node.setIndex(index);
        node.setHost(MYSQL.getHost());
        node.setPort(MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT));
        node.setDatabase(database);
        node.setUsername(MYSQL.getUsername());
        node.setPassword(MYSQL.getPassword());
        return node;
    }

    private static DriverManagerDataSource dataSource(String database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true"
                .formatted(MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT), database));
        dataSource.setUsername(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
        return dataSource;
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
}
