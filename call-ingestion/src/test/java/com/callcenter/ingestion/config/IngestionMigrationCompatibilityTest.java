package com.callcenter.ingestion.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionMigrationCompatibilityTest {

    @Test
    void migrationShouldAvoidUnsupportedAddColumnIfNotExistsSyntax() throws IOException {
        String migration = new ClassPathResource("db/migration/V1__init_call_ingestion_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration).doesNotContain("ADD COLUMN IF NOT EXISTS");
        assertThat(migration).contains("DROP PROCEDURE IF EXISTS alter_call_record_tables");
    }
}
