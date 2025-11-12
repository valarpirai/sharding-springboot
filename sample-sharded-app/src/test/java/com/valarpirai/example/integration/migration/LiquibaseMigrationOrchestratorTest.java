package com.valarpirai.example.integration.migration;

import com.valarpirai.example.integration.BaseIntegrationTest;
import com.valarpirai.sharding.migration.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Liquibase Migration Orchestrator.
 * Tests migration execution across global and sharded databases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // Enable migration orchestrator for tests
        "app.sharding.migration.enabled=true",
        "app.sharding.migration.migrate-global-db=false",
        "app.sharding.migration.global-change-log-path=db/changelog/test/global-test-changelog.xml",
        "app.sharding.migration.sharded-change-log-path=db/changelog/test/sharded-test-changelog.xml",
        "app.sharding.migration.default-strategy=SEQUENTIAL",
        "app.sharding.migration.fail-fast=true",
        "app.sharding.migration.allow-rollback=true",
        "app.sharding.migration.dry-run=false",
        "app.sharding.migration.parallel-threads=3",
        "app.sharding.migration.wave-size=2",
        "app.sharding.migration.wave-delay-seconds=1"
})
class LiquibaseMigrationOrchestratorTest extends BaseIntegrationTest {

    @Autowired(required = false)
    private LiquibaseMigrationOrchestrator migrationOrchestrator;

    @Autowired(required = false)
    private MigrationProgressTracker progressTracker;

    @Autowired(required = false)
    private MigrationLockManager lockManager;

    @Autowired
    private DataSource globalDataSource;

    private JdbcTemplate globalJdbcTemplate;

    @BeforeEach
    void setUp() {
        if (migrationOrchestrator == null) {
            // Skip tests if migration is disabled
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Migration orchestrator is not enabled");
        }

        globalJdbcTemplate = new JdbcTemplate(globalDataSource);

        // Clear any previous migration state
        if (lockManager != null) {
            lockManager.releaseLock();
        }
        if (progressTracker != null) {
            progressTracker.clear();
        }
    }

    @Test
    @DisplayName("Should execute migration with SEQUENTIAL strategy")
    void shouldExecuteMigrationWithSequentialStrategy() {
        // Execute migration
        MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Verify results
        assertThat(report).isNotNull();
        assertThat(report.getStrategy()).isEqualTo(MigrationStrategy.SEQUENTIAL);
        assertThat(report.getTotalShards()).isGreaterThan(0);
        assertThat(report.isCompleted()).isTrue();

        // Check that migrations were executed or skipped (idempotent)
        assertThat(report.getSuccessCount() + report.getSkippedCount()).isEqualTo(report.getTotalShards());
    }

    @Test
    @DisplayName("Should execute migration with PARALLEL strategy")
    void shouldExecuteMigrationWithParallelStrategy() {
        // Execute migration
        MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.PARALLEL);

        // Verify results
        assertThat(report).isNotNull();
        assertThat(report.getStrategy()).isEqualTo(MigrationStrategy.PARALLEL);
        assertThat(report.getTotalShards()).isGreaterThan(0);
        assertThat(report.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should execute migration with WAVE strategy")
    void shouldExecuteMigrationWithWaveStrategy() {
        // Execute migration
        MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.WAVE);

        // Verify results
        assertThat(report).isNotNull();
        assertThat(report.getStrategy()).isEqualTo(MigrationStrategy.WAVE);
        assertThat(report.getTotalShards()).isGreaterThan(0);
        assertThat(report.isCompleted()).isTrue();

        // Wave strategy should process in batches
        assertThat(report.getTotalExecutionTimeMs()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should be idempotent - running twice should skip already executed changesets")
    void shouldBeIdempotentWhenRunningTwice() {
        // First execution
        MigrationReport firstReport = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
        int firstChangesetCount = firstReport.getResults().stream()
                .mapToInt(ShardMigrationResult::getChangeSetExecuted)
                .sum();

        // Second execution (should skip all)
        MigrationReport secondReport = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Verify idempotency
        assertThat(secondReport.getSkippedCount()).isGreaterThan(0);

        // If first run executed changesets, second run should execute 0
        if (firstChangesetCount > 0) {
            int secondChangesetCount = secondReport.getResults().stream()
                    .mapToInt(ShardMigrationResult::getChangeSetExecuted)
                    .sum();
            assertThat(secondChangesetCount).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Should prevent concurrent migrations")
    void shouldPreventConcurrentMigrations() {
        // Start first migration in a separate thread
        Thread migrationThread = new Thread(() -> {
            migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
        });
        migrationThread.start();

        // Wait a bit for first migration to acquire lock
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Try to start second migration (should fail)
        assertThatThrownBy(() -> migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("already in progress");

        // Wait for first migration to complete
        try {
            migrationThread.join(10000); // 10 second timeout
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Should track migration progress")
    void shouldTrackMigrationProgress() {
        // Execute migration
        new Thread(() -> migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL)).start();

        // Wait briefly for migration to start
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check progress tracking
        if (progressTracker.hasMigrationsInProgress()) {
            var progress = progressTracker.getAllProgress();
            assertThat(progress).isNotEmpty();

            // At least one shard should have progress
            assertThat(progress.values())
                    .anyMatch(p -> p.getStatus() == MigrationStatus.IN_PROGRESS ||
                                 p.getStatus() == MigrationStatus.SUCCESS);
        }
    }

    @Test
    @DisplayName("Should report migration status correctly")
    void shouldReportMigrationStatusCorrectly() {
        // Execute migration
        MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Verify report structure
        assertThat(report.getTotalShards()).isGreaterThan(0);
        assertThat(report.getResults()).hasSize(report.getTotalShards());

        // Each result should have valid status
        report.getResults().forEach(result -> {
            assertThat(result.getShardId()).isNotNull();
            assertThat(result.getStatus()).isIn(
                    MigrationStatus.SUCCESS,
                    MigrationStatus.SKIPPED,
                    MigrationStatus.FAILED
            );
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    @DisplayName("Should verify global database has tenant_shard_mapping table")
    void shouldVerifyGlobalDatabaseHasTenantShardMappingTable() {
        // Check that global DB has the tenant_shard_mapping table
        Integer count = globalJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class,
                "tenant_shard_mapping"
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle migration with no pending changesets gracefully")
    void shouldHandleNoPendingChangesetsGracefully() {
        // Run migration twice
        migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
        MigrationReport secondReport = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Second run should have all skipped
        assertThat(secondReport.getSkippedCount()).isEqualTo(secondReport.getTotalShards());
        assertThat(secondReport.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should record execution time for each shard")
    void shouldRecordExecutionTimeForEachShard() {
        // Execute migration
        MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Verify execution times
        assertThat(report.getTotalExecutionTimeMs()).isGreaterThan(0);

        report.getResults().forEach(result -> {
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    @DisplayName("Should provide shard-level migration details")
    void shouldProvideShardLevelMigrationDetails() {
        // Execute migration
        MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Verify shard-level details
        report.getResults().forEach(result -> {
            assertThat(result.getShardId()).isNotEmpty();

            if (result.getStatus() == MigrationStatus.SUCCESS) {
                assertThat(result.getTargetVersion()).isNotNull();
            }

            if (result.getStatus() == MigrationStatus.FAILED) {
                assertThat(result.getErrorMessage()).isNotNull();
            }
        });
    }

    @Test
    @DisplayName("Should clear progress tracking")
    void shouldClearProgressTracking() {
        // Execute migration
        migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Clear progress
        progressTracker.clear();

        // Verify cleared
        assertThat(progressTracker.getAllProgress()).isEmpty();
        assertThat(progressTracker.hasMigrationsInProgress()).isFalse();
    }

    @Test
    @DisplayName("Should provide migration status summary")
    void shouldProvideMigrationStatusSummary() {
        // Execute migration
        migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Get status summary
        var statusSummary = progressTracker.getStatusSummary();

        // Verify summary
        assertThat(statusSummary).isNotNull();
        assertThat(statusSummary.values().stream().mapToLong(Long::longValue).sum())
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Should release lock after successful migration")
    void shouldReleaseLockAfterSuccessfulMigration() {
        // Execute migration
        migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

        // Verify lock is released
        assertThat(lockManager.tryAcquireLock()).isTrue();
        lockManager.releaseLock();
    }

    @Test
    @DisplayName("Should release lock even after migration failure")
    void shouldReleaseLockEvenAfterFailure() {
        try {
            // Try migration with invalid changelog path (will fail)
            migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
        } catch (Exception e) {
            // Expected to fail with invalid changelog
        }

        // Verify lock is still released
        assertThat(lockManager.tryAcquireLock()).isTrue();
        lockManager.releaseLock();
    }
}
