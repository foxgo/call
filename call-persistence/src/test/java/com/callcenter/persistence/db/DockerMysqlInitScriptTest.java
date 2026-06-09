package com.callcenter.persistence.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DockerMysqlInitScriptTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("mysql")
            .withUsername("root")
            .withPassword("root123");

    @Test
    void shouldInitializeCallDatabasesAndMonthlyTables() throws Exception {
        DataSource dataSource = dataSource("mysql");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new FileSystemResource(Path.of("..", "deploy", "docker", "mysql", "init", "001_create_databases.sql"))
            );
        }

        for (String database : List.of("call_0", "call_1", "call_2", "call_3")) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource(database));
            assertThat(tableExists(jdbcTemplate, "call_record_202605_00")).isTrue();
            assertThat(tableExists(jdbcTemplate, "call_record_202605_15")).isTrue();
            assertThat(tableExists(jdbcTemplate, "call_round_202605_00")).isTrue();
            assertThat(tableExists(jdbcTemplate, "call_round_202605_15")).isTrue();
        }
    }

    private static DataSource dataSource(String database) {
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
