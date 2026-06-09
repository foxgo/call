package com.callcenter.iam.config;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallIamFlywayConfigTest {

    @Test
    void shouldUseLegacyBaselineWhenIamSchemaAlreadyExistsWithoutCustomHistoryTable() {
        assertThat(CallIamFlywayConfig.planForState(new CallIamFlywayConfig.MigrationState(false, false, false, true)).baselineVersion())
                .isEqualTo("1");
    }

    @Test
    void shouldKeepFreshBaselineWhenOnlyUnrelatedTablesExist() {
        assertThat(CallIamFlywayConfig.planForState(new CallIamFlywayConfig.MigrationState(false, false, false, false)).baselineVersion())
                .isEqualTo("0");
    }

    @Test
    void shouldContinueNormalMigrationWhenHistoryAlreadyHasSuccessfulEntries() {
        assertThat(CallIamFlywayConfig.planForState(new CallIamFlywayConfig.MigrationState(true, true, false, true)).baselineVersion())
                .isEqualTo("0");
    }

    @Test
    void shouldRepairAndUseLegacyBaselineWhenFailedInitMigrationExistsForLegacySchema() {
        CallIamFlywayConfig.MigrationPlan plan =
                CallIamFlywayConfig.planForState(new CallIamFlywayConfig.MigrationState(true, false, true, true));

        assertThat(plan.baselineVersion()).isEqualTo("1");
        assertThat(plan.repairBeforeMigrate()).isTrue();
    }

    @Test
    void shouldNotRepairWhenHistoryTableExistsWithoutFailedInitMigration() {
        CallIamFlywayConfig.MigrationPlan plan =
                CallIamFlywayConfig.planForState(new CallIamFlywayConfig.MigrationState(true, false, false, true));

        assertThat(plan.baselineVersion()).isEqualTo("1");
        assertThat(plan.repairBeforeMigrate()).isFalse();
    }

    @Test
    void shouldRepairFailedInitMigrationEvenWhenLegacyTablesDoNotExist() {
        CallIamFlywayConfig.MigrationPlan plan =
                CallIamFlywayConfig.planForState(new CallIamFlywayConfig.MigrationState(true, false, true, false));

        assertThat(plan.baselineVersion()).isEqualTo("0");
        assertThat(plan.repairBeforeMigrate()).isTrue();
    }
}
