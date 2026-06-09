package com.callcenter.task.config;

import com.callcenter.persistence.config.CallDatasourceProperties;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CallTaskFlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(CallDatasourceProperties properties) {
        return ignored -> properties.getNodes().forEach(this::migrateNode);
    }

    private void migrateNode(CallDatasourceProperties.Node node) {
        Flyway.configure()
                .dataSource(jdbcUrl(node), node.getUsername(), node.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    private String jdbcUrl(CallDatasourceProperties.Node node) {
        return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true"
                .formatted(node.getHost(), node.getPort(), node.getDatabase());
    }
}
