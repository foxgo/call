package com.callcenter.iam.config;

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
class IamFlywayMigrationStrategyTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("mysql")
            .withUsername("root")
            .withPassword("root123");

    @Test
    void shouldMigrateConfiguredIamDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            connection.createStatement().execute("CREATE DATABASE call_0");
            connection.createStatement().execute("""
                    CREATE TABLE call_0.call_record_202605_00 (
                        call_id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        phone VARCHAR(20) NOT NULL,
                        round_total INT
                    )
                    """);
        }

        CallDatasourceProperties properties = new CallDatasourceProperties();
        properties.setNodes(List.of(node(0, "call_0")));

        FlywayMigrationStrategy strategy = new CallIamFlywayConfig().flywayMigrationStrategy(properties);
        strategy.migrate(null);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("call_0"));
        assertThat(tableExists(jdbcTemplate, "flyway_schema_history_call_iam")).isTrue();
        assertThat(tableExists(jdbcTemplate, "flyway_schema_history")).isFalse();
        assertThat(tableExists(jdbcTemplate, "tenant")).isTrue();
        assertThat(tableExists(jdbcTemplate, "permission")).isTrue();
        assertThat(tableExists(jdbcTemplate, "role")).isTrue();
        assertThat(tableExists(jdbcTemplate, "iam_user")).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permission", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tenant", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam_user", Integer.class)).isEqualTo(2);
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
