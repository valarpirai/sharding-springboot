package com.valarpirai.sharding.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Real-time progress tracking for a shard migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationProgress {
    private String shardId;
    private MigrationStatus status;
    private String currentVersion;
    private String targetVersion;
    private int totalChangeSets;
    private int executedChangeSets;
    private Instant startTime;
    private Instant lastUpdateTime;
    private String currentChangeSet;
    private String errorMessage;

    public double getProgressPercentage() {
        if (totalChangeSets == 0) return 0.0;
        return (double) executedChangeSets / totalChangeSets * 100;
    }

    public long getElapsedTimeMs() {
        if (startTime == null) return 0;
        Instant endTime = lastUpdateTime != null ? lastUpdateTime : Instant.now();
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
}
