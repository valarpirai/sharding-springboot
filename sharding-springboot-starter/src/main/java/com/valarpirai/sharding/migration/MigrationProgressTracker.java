package com.valarpirai.sharding.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks real-time progress of migrations across all shards.
 */
@Component
public class MigrationProgressTracker {

    private static final Logger log = LoggerFactory.getLogger(MigrationProgressTracker.class);

    private final Map<String, MigrationProgress> progressMap = new ConcurrentHashMap<>();

    /**
     * Start tracking a migration for a shard.
     */
    public void startMigration(String shardId, String currentVersion, String targetVersion, int totalChangeSets) {
        MigrationProgress progress = MigrationProgress.builder()
                .shardId(shardId)
                .status(MigrationStatus.IN_PROGRESS)
                .currentVersion(currentVersion)
                .targetVersion(targetVersion)
                .totalChangeSets(totalChangeSets)
                .executedChangeSets(0)
                .startTime(Instant.now())
                .lastUpdateTime(Instant.now())
                .build();

        progressMap.put(shardId, progress);
        log.info("Started migration tracking for shard: {} (v{} -> v{})",
                 shardId, currentVersion, targetVersion);
    }

    /**
     * Update progress for a shard.
     */
    public void updateProgress(String shardId, int executedChangeSets, String currentChangeSet) {
        MigrationProgress progress = progressMap.get(shardId);
        if (progress != null) {
            progress.setExecutedChangeSets(executedChangeSets);
            progress.setCurrentChangeSet(currentChangeSet);
            progress.setLastUpdateTime(Instant.now());

            log.debug("Migration progress for shard {}: {}/{} change sets ({}%)",
                     shardId, executedChangeSets, progress.getTotalChangeSets(),
                     String.format("%.1f", progress.getProgressPercentage()));
        }
    }

    /**
     * Mark migration as completed successfully.
     */
    public void completeMigration(String shardId, String finalVersion) {
        MigrationProgress progress = progressMap.get(shardId);
        if (progress != null) {
            progress.setStatus(MigrationStatus.SUCCESS);
            progress.setCurrentVersion(finalVersion);
            progress.setLastUpdateTime(Instant.now());

            log.info("Completed migration for shard: {} to version {} in {}ms",
                     shardId, finalVersion, progress.getElapsedTimeMs());
        }
    }

    /**
     * Mark migration as failed.
     */
    public void failMigration(String shardId, String errorMessage) {
        MigrationProgress progress = progressMap.get(shardId);
        if (progress != null) {
            progress.setStatus(MigrationStatus.FAILED);
            progress.setErrorMessage(errorMessage);
            progress.setLastUpdateTime(Instant.now());

            log.error("Migration failed for shard: {} - {}", shardId, errorMessage);
        }
    }

    /**
     * Mark migration as skipped.
     */
    public void skipMigration(String shardId, String reason) {
        MigrationProgress progress = progressMap.get(shardId);
        if (progress != null) {
            progress.setStatus(MigrationStatus.SKIPPED);
            progress.setErrorMessage(reason);
            progress.setLastUpdateTime(Instant.now());

            log.info("Skipped migration for shard: {} - {}", shardId, reason);
        }
    }

    /**
     * Get progress for a specific shard.
     */
    public MigrationProgress getProgress(String shardId) {
        return progressMap.get(shardId);
    }

    /**
     * Get progress for all shards.
     */
    public Map<String, MigrationProgress> getAllProgress() {
        return Collections.unmodifiableMap(progressMap);
    }

    /**
     * Get count of shards by status.
     */
    public Map<MigrationStatus, Long> getStatusSummary() {
        return progressMap.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        MigrationProgress::getStatus,
                        java.util.stream.Collectors.counting()
                ));
    }

    /**
     * Clear all progress tracking.
     */
    public void clear() {
        progressMap.clear();
        log.info("Cleared all migration progress tracking");
    }

    /**
     * Clear progress for a specific shard.
     */
    public void clear(String shardId) {
        progressMap.remove(shardId);
        log.debug("Cleared migration progress for shard: {}", shardId);
    }

    /**
     * Check if any migrations are in progress.
     */
    public boolean hasMigrationsInProgress() {
        return progressMap.values().stream()
                .anyMatch(p -> p.getStatus() == MigrationStatus.IN_PROGRESS);
    }
}
