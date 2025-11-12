package com.valarpirai.sharding.migration;

import com.valarpirai.sharding.config.DatabaseConfig;
import com.valarpirai.sharding.config.ShardConfig;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Orchestrates Liquibase migrations across multiple shards with various strategies.
 */
@Slf4j
@Service
public class LiquibaseMigrationOrchestrator {

    private final ShardingConfigProperties shardingProperties;
    private final MigrationProgressTracker progressTracker;
    private final LiquibaseMigrationConfig migrationConfig;
    private final MigrationLockManager lockManager;
    private final ExecutorService executorService;

    @Autowired
    public LiquibaseMigrationOrchestrator(
            ShardingConfigProperties shardingProperties,
            MigrationProgressTracker progressTracker,
            LiquibaseMigrationConfig migrationConfig,
            MigrationLockManager lockManager) {
        this.shardingProperties = shardingProperties;
        this.progressTracker = progressTracker;
        this.migrationConfig = migrationConfig;
        this.lockManager = lockManager;
        this.executorService = Executors.newFixedThreadPool(
                migrationConfig.getParallelThreads()
        );
    }

    /**
     * Execute migrations on all shards using the specified strategy.
     * This method is idempotent - calling it multiple times will not cause issues.
     * Already-executed changesets will be skipped automatically by Liquibase.
     */
    public MigrationReport migrateAll(MigrationStrategy strategy) {
        // Try to acquire lock to prevent concurrent executions
        if (!lockManager.tryAcquireLock()) {
            throw new MigrationException("Migration already in progress. Cannot start concurrent migration.");
        }

        log.info("Starting migration with strategy: {}", strategy);

        MigrationReport report = new MigrationReport();
        report.setStrategy(strategy);

        try {
            // 1. Migrate global database first if enabled
            if (migrationConfig.isMigrateGlobalDb()) {
                migrateGlobalDatabase(report);
            }

            // 2. Get all shards
            List<ShardInfo> shards = getAllShards();
            report.setTotalShards(shards.size());

            // 3. Execute based on strategy
            switch (strategy) {
                case SEQUENTIAL:
                    migrateSequential(shards, report);
                    break;
                case PARALLEL:
                    migrateParallel(shards, report);
                    break;
                case WAVE:
                    migrateInWaves(shards, report);
                    break;
                case CANARY:
                    migrateCanary(shards, report);
                    break;
            }

        } catch (Exception e) {
            log.error("Migration execution failed", e);
            throw new MigrationException("Migration execution failed", e);
        } finally {
            report.complete();
            lockManager.releaseLock();
        }

        log.info("Migration completed: {} successful, {} failed, {} skipped in {}ms",
                 report.getSuccessCount(), report.getFailureCount(),
                 report.getSkippedCount(), report.getTotalExecutionTimeMs());

        return report;
    }

    /**
     * Migrate global database.
     */
    private void migrateGlobalDatabase(MigrationReport report) {
        log.info("Migrating global database");

        ShardMigrationResult result = migrateSingleDatabase(
                "global",
                shardingProperties.getGlobalDb().getUrl(),
                shardingProperties.getGlobalDb().getUsername(),
                shardingProperties.getGlobalDb().getPassword(),
                migrationConfig.getGlobalChangeLogPath(),
                "global"
        );

        report.addResult(result);

        if (result.isFailed()) {
            throw new MigrationException("Global database migration failed: " + result.getErrorMessage());
        }
    }

    /**
     * Migrate shards sequentially (one at a time).
     */
    private void migrateSequential(List<ShardInfo> shards, MigrationReport report) {
        log.info("Migrating {} shards sequentially", shards.size());

        for (ShardInfo shard : shards) {
            ShardMigrationResult result = migrateShard(shard);
            report.addResult(result);

            // Stop on first failure if fail-fast is enabled
            if (result.isFailed() && migrationConfig.isFailFast()) {
                log.error("Migration failed for shard {}, stopping due to fail-fast mode", shard.getShardId());
                break;
            }
        }
    }

    /**
     * Migrate all shards in parallel.
     */
    private void migrateParallel(List<ShardInfo> shards, MigrationReport report) {
        log.info("Migrating {} shards in parallel", shards.size());

        List<CompletableFuture<ShardMigrationResult>> futures = shards.stream()
                .map(shard -> CompletableFuture.supplyAsync(
                        () -> migrateShard(shard),
                        executorService
                ))
                .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results
        futures.forEach(future -> {
            try {
                report.addResult(future.get());
            } catch (Exception e) {
                log.error("Error collecting migration result", e);
            }
        });
    }

    /**
     * Migrate shards in waves/batches.
     */
    private void migrateInWaves(List<ShardInfo> shards, MigrationReport report) {
        int waveSize = migrationConfig.getWaveSize();
        log.info("Migrating {} shards in waves of {}", shards.size(), waveSize);

        for (int i = 0; i < shards.size(); i += waveSize) {
            int waveNumber = (i / waveSize) + 1;
            List<ShardInfo> wave = shards.subList(i, Math.min(i + waveSize, shards.size()));

            log.info("Starting wave {} with {} shards", waveNumber, wave.size());

            // Create sub-report for this wave
            MigrationReport waveReport = new MigrationReport();
            waveReport.setTotalShards(wave.size());

            migrateParallel(wave, waveReport);

            // Add wave results to main report
            waveReport.getResults().forEach(report::addResult);

            // Check for failures before next wave
            if (waveReport.hasFailures() && migrationConfig.isFailFast()) {
                log.error("Wave {} failed, stopping due to fail-fast mode", waveNumber);
                break;
            }

            // Wait between waves if configured
            if (migrationConfig.getWaveDelaySeconds() > 0 && i + waveSize < shards.size()) {
                log.info("Waiting {}s before next wave", migrationConfig.getWaveDelaySeconds());
                sleep(migrationConfig.getWaveDelaySeconds() * 1000L);
            }
        }
    }

    /**
     * Canary migration: test on one shard first, then proceed with others.
     */
    private void migrateCanary(List<ShardInfo> shards, MigrationReport report) {
        if (shards.isEmpty()) {
            return;
        }

        log.info("Starting canary migration with {} total shards", shards.size());

        // 1. Select canary shard (first shard or configured canary)
        ShardInfo canaryShard = selectCanaryShard(shards);
        log.info("Selected canary shard: {}", canaryShard.getShardId());

        // 2. Migrate canary
        ShardMigrationResult canaryResult = migrateShard(canaryShard);
        report.addResult(canaryResult);

        if (canaryResult.isFailed()) {
            log.error("Canary migration failed, aborting");
            throw new MigrationException("Canary migration failed: " + canaryResult.getErrorMessage());
        }

        log.info("Canary migration successful");

        // 3. Wait for validation period
        if (migrationConfig.getCanaryValidationMinutes() > 0) {
            log.info("Waiting {} minutes for canary validation",
                     migrationConfig.getCanaryValidationMinutes());
            sleep(migrationConfig.getCanaryValidationMinutes() * 60 * 1000L);
        }

        // 4. Proceed with remaining shards
        List<ShardInfo> remainingShards = shards.stream()
                .filter(s -> !s.getShardId().equals(canaryShard.getShardId()))
                .collect(Collectors.toList());

        log.info("Proceeding with {} remaining shards", remainingShards.size());

        if (migrationConfig.getCanaryRolloutStrategy() == CanaryRolloutStrategy.PARALLEL) {
            migrateParallel(remainingShards, report);
        } else {
            migrateInWaves(remainingShards, report);
        }
    }

    /**
     * Migrate a single shard.
     */
    private ShardMigrationResult migrateShard(ShardInfo shard) {
        return migrateSingleDatabase(
                shard.getShardId(),
                shard.getUrl(),
                shard.getUsername(),
                shard.getPassword(),
                migrationConfig.getShardedChangeLogPath(),
                "sharded"
        );
    }

    /**
     * Core method to migrate a single database.
     */
    private ShardMigrationResult migrateSingleDatabase(
            String dbId,
            String url,
            String username,
            String password,
            String changeLogPath,
            String contexts) {

        Instant startTime = Instant.now();
        progressTracker.startMigration(dbId, "unknown", "target", 0);

        try (HikariDataSource dataSource = createTemporaryDataSource(url, username, password);
             Connection connection = dataSource.getConnection()) {

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            try (Liquibase liquibase = new Liquibase(
                    changeLogPath,
                    new ClassLoaderResourceAccessor(),
                    database)) {

                // Get current version
                String currentVersion = getCurrentVersion(liquibase);

                // Get unrun change sets count
                int unrunChangeSets = liquibase.listUnrunChangeSets(
                        new Contexts(contexts),
                        new LabelExpression()
                ).size();

                log.info("Database {}: {} unrun change sets", dbId, unrunChangeSets);

                if (unrunChangeSets == 0) {
                    progressTracker.skipMigration(dbId, "No pending migrations");
                    return ShardMigrationResult.skipped(dbId, "No pending migrations");
                }

                // Execute migration
                liquibase.update(new Contexts(contexts), new LabelExpression());

                // Get final version
                String finalVersion = getCurrentVersion(liquibase);

                long executionTime = Duration.between(startTime, Instant.now()).toMillis();

                progressTracker.completeMigration(dbId, finalVersion);

                return ShardMigrationResult.success(
                        dbId,
                        unrunChangeSets,
                        finalVersion,
                        executionTime
                );

            }

        } catch (Exception e) {
            log.error("Migration failed for database: {}", dbId, e);
            progressTracker.failMigration(dbId, e.getMessage());

            return ShardMigrationResult.failure(
                    dbId,
                    e.getMessage(),
                    getStackTrace(e)
            );
        }
    }

    /**
     * Get current database version from Liquibase.
     */
    private String getCurrentVersion(Liquibase liquibase) {
        try {
            var ranChangeSets = liquibase.getDatabase().getRanChangeSetList();
            if (ranChangeSets == null || ranChangeSets.isEmpty()) {
                return "0.0.0";
            }
            var lastChangeSet = ranChangeSets.get(ranChangeSets.size() - 1);
            return lastChangeSet.getId();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Create a temporary DataSource for migration.
     */
    private HikariDataSource createTemporaryDataSource(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        return new HikariDataSource(config);
    }

    /**
     * Get all shards as ShardInfo objects.
     */
    private List<ShardInfo> getAllShards() {
        List<ShardInfo> shards = new ArrayList<>();

        shardingProperties.getShards().forEach((shardId, shardConfig) -> {
            DatabaseConfig master = shardConfig.getMaster();
            shards.add(new ShardInfo(
                    shardId,
                    master.getUrl(),
                    master.getUsername(),
                    master.getPassword()
            ));
        });

        return shards;
    }

    /**
     * Select canary shard.
     */
    private ShardInfo selectCanaryShard(List<ShardInfo> shards) {
        // For now, select the first shard
        // TODO: Allow configuration of canary shard
        return shards.get(0);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MigrationException("Migration interrupted", e);
        }
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Internal class to hold shard connection info.
     */
    private static class ShardInfo {
        private final String shardId;
        private final String url;
        private final String username;
        private final String password;

        public ShardInfo(String shardId, String url, String username, String password) {
            this.shardId = shardId;
            this.url = url;
            this.username = username;
            this.password = password;
        }

        public String getShardId() { return shardId; }
        public String getUrl() { return url; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }

    /**
     * Rollback migrations based on request.
     */
    public MigrationReport rollback(RollbackRequest request) {
        if (!migrationConfig.isAllowRollback()) {
            throw new MigrationException("Rollback is not enabled. Set app.sharding.migration.allow-rollback=true");
        }

        // Try to acquire lock to prevent concurrent executions
        if (!lockManager.tryAcquireLock()) {
            throw new MigrationException("Migration or rollback already in progress. Cannot start concurrent operation.");
        }

        log.info("Starting rollback with type: {}", request.getType());

        MigrationReport report = new MigrationReport();
        report.setStrategy(MigrationStrategy.SEQUENTIAL); // Rollbacks are always sequential for safety

        try {
            List<ShardInfo> shards = getAllShards();

            // Filter shards if specific ones are requested
            if (request.getShardIds() != null && !request.getShardIds().isEmpty()) {
                shards = shards.stream()
                        .filter(s -> request.getShardIds().contains(s.getShardId()))
                        .collect(Collectors.toList());
            }

            report.setTotalShards(shards.size());

            for (ShardInfo shard : shards) {
                ShardMigrationResult result = rollbackSingleShard(shard, request);
                report.addResult(result);

                if (result.isFailed() && migrationConfig.isFailFast()) {
                    log.error("Rollback failed for shard {}, stopping", shard.getShardId());
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Rollback execution failed", e);
            throw new MigrationException("Rollback execution failed", e);
        } finally {
            report.complete();
            lockManager.releaseLock();
        }

        log.info("Rollback completed: {} successful, {} failed in {}ms",
                 report.getSuccessCount(), report.getFailureCount(),
                 report.getTotalExecutionTimeMs());

        return report;
    }

    /**
     * Rollback a single shard.
     */
    private ShardMigrationResult rollbackSingleShard(ShardInfo shard, RollbackRequest request) {
        Instant startTime = Instant.now();
        progressTracker.startMigration(shard.getShardId(), "rollback", "target", 0);

        try (HikariDataSource dataSource = createTemporaryDataSource(
                shard.getUrl(), shard.getUsername(), shard.getPassword());
             Connection connection = dataSource.getConnection()) {

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            try (Liquibase liquibase = new Liquibase(
                    migrationConfig.getShardedChangeLogPath(),
                    new ClassLoaderResourceAccessor(),
                    database)) {

                String currentVersion = getCurrentVersion(liquibase);

                // Perform rollback based on type
                switch (request.getType()) {
                    case COUNT:
                        liquibase.rollback(request.getCount(), migrationConfig.getContexts());
                        break;
                    case TAG:
                        liquibase.rollback(request.getTag(), migrationConfig.getContexts());
                        break;
                    case DATE:
                        // Note: Liquibase date rollback requires java.util.Date
                        throw new MigrationException("Date rollback not yet implemented");
                }

                String finalVersion = getCurrentVersion(liquibase);
                long executionTime = Duration.between(startTime, Instant.now()).toMillis();

                progressTracker.completeMigration(shard.getShardId(), finalVersion);

                return ShardMigrationResult.builder()
                        .shardId(shard.getShardId())
                        .status(MigrationStatus.ROLLED_BACK)
                        .currentVersion(currentVersion)
                        .targetVersion(finalVersion)
                        .executionTimeMs(executionTime)
                        .build();

            }

        } catch (Exception e) {
            log.error("Rollback failed for shard: {}", shard.getShardId(), e);
            progressTracker.failMigration(shard.getShardId(), e.getMessage());

            return ShardMigrationResult.failure(
                    shard.getShardId(),
                    e.getMessage(),
                    getStackTrace(e)
            );
        }
    }

    /**
     * Canary rollout strategy after successful canary migration.
     */
    public enum CanaryRolloutStrategy {
        PARALLEL,  // Roll out to all remaining shards in parallel
        WAVE       // Roll out in waves
    }
}
