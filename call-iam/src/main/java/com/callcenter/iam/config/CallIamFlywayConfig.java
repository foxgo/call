package com.callcenter.iam.config;

import com.callcenter.persistence.config.CallDatasourceProperties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CallIamFlywayConfig {

    static final String HISTORY_TABLE = "flyway_schema_history_call_iam";
    private static final Set<String> LEGACY_IAM_TABLES = Set.of(
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

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(CallDatasourceProperties properties) {
        return ignored -> properties.getNodes().forEach(this::migrateNode);
    }

    private void migrateNode(CallDatasourceProperties.Node node) {
        MigrationPlan plan = inspectMigrationPlan(node);
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(node), node.getUsername(), node.getPassword())
                .locations("classpath:db/migration")
                .table(HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion(plan.baselineVersion())
                .load()
                ;
        if (plan.repairBeforeMigrate()) {
            flyway.repair();
        }
        flyway.migrate();
    }

    private MigrationPlan inspectMigrationPlan(CallDatasourceProperties.Node node) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(node), node.getUsername(), node.getPassword())) {
            return planForState(loadMigrationState(connection));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to inspect existing IAM schema for Flyway baseline", ex);
        }
    }

    static MigrationPlan planForState(MigrationState state) {
        if (state.hasSuccessfulHistory()) {
            return new MigrationPlan("0", false);
        }
        if (state.hasFailedInitMigration()) {
            return new MigrationPlan(state.hasLegacyIamTables() ? "1" : "0", true);
        }
        if (state.hasLegacyIamTables()) {
            return new MigrationPlan("1", false);
        }
        return new MigrationPlan("0", false);
    }

    private MigrationState loadMigrationState(Connection connection) throws Exception {
        Set<String> normalizedTables = loadTableNames(connection).stream()
                .map(table -> table.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean hasHistoryTable = normalizedTables.contains(HISTORY_TABLE);
        boolean hasLegacyIamTables = normalizedTables.stream().anyMatch(LEGACY_IAM_TABLES::contains);
        if (!hasHistoryTable) {
            return new MigrationState(false, false, false, hasLegacyIamTables);
        }
        return new MigrationState(
                true,
                hasSuccessfulHistory(connection),
                hasFailedInitMigration(connection),
                hasLegacyIamTables
        );
    }

    private Set<String> loadTableNames(Connection connection) throws Exception {
        Set<String> tables = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private boolean hasSuccessfulHistory(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM " + HISTORY_TABLE + " WHERE success = 1");
             var resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    private boolean hasFailedInitMigration(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + HISTORY_TABLE + " WHERE version = ? AND success = 0"
        )) {
            statement.setString(1, "1");
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private String jdbcUrl(CallDatasourceProperties.Node node) {
        return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true"
                .formatted(node.getHost(), node.getPort(), node.getDatabase());
    }

    record MigrationState(
            boolean hasHistoryTable,
            boolean hasSuccessfulHistory,
            boolean hasFailedInitMigration,
            boolean hasLegacyIamTables
    ) {
    }

    record MigrationPlan(String baselineVersion, boolean repairBeforeMigrate) {
    }
}
