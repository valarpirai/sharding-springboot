package com.valarpirai.sharding.migration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Liquibase migrations.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sharding.migration")
public class LiquibaseMigrationConfig {

    /**
     * Enable or disable automatic migrations on startup.
     */
    private boolean enabled = false;

    /**
     * Migrate global database.
     */
    private boolean migrateGlobalDb = true;

    /**
     * Path to the global database changelog file.
     */
    private String globalChangeLogPath = "db/changelog/global/master-changelog.xml";

    /**
     * Path to the sharded database changelog file.
     */
    private String shardedChangeLogPath = "db/changelog/sharded/master-changelog.xml";

    /**
     * Default migration strategy.
     */
    private MigrationStrategy defaultStrategy = MigrationStrategy.WAVE;

    /**
     * Number of parallel threads for concurrent migrations.
     */
    private int parallelThreads = 5;

    /**
     * Wave size for WAVE strategy (number of shards per wave).
     */
    private int waveSize = 5;

    /**
     * Delay between waves in seconds.
     */
    private int waveDelaySeconds = 30;

    /**
     * Canary validation period in minutes.
     */
    private int canaryValidationMinutes = 5;

    /**
     * Rollout strategy after canary succeeds.
     */
    private LiquibaseMigrationOrchestrator.CanaryRolloutStrategy canaryRolloutStrategy =
            LiquibaseMigrationOrchestrator.CanaryRolloutStrategy.WAVE;

    /**
     * Stop migration on first failure.
     */
    private boolean failFast = true;

    /**
     * Perform validation before migration.
     */
    private boolean validateBeforeMigration = true;

    /**
     * Allow rollback operations.
     */
    private boolean allowRollback = false;

    /**
     * Dry run mode (no actual changes).
     */
    private boolean dryRun = false;

    /**
     * Liquibase contexts to use (comma-separated).
     */
    private String contexts = "default";

    /**
     * Liquibase labels to use (comma-separated).
     */
    private String labels = "";
}
