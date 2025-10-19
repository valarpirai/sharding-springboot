package com.valarpirai.sharding.migration;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comprehensive report of migration execution across all shards.
 */
@Data
public class MigrationReport {
    private Instant startTime;
    private Instant endTime;
    private MigrationStrategy strategy;
    private List<ShardMigrationResult> results = new ArrayList<>();
    private int totalShards;
    private int successCount;
    private int failureCount;
    private int skippedCount;
    private long totalExecutionTimeMs;

    public MigrationReport() {
        this.startTime = Instant.now();
    }

    public void addResult(ShardMigrationResult result) {
        results.add(result);
        updateCounters(result);
    }

    private void updateCounters(ShardMigrationResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                successCount++;
                break;
            case FAILED:
                failureCount++;
                break;
            case SKIPPED:
                skippedCount++;
                break;
        }
    }

    public void complete() {
        this.endTime = Instant.now();
        this.totalExecutionTimeMs = endTime.toEpochMilli() - startTime.toEpochMilli();
    }

    public boolean hasFailures() {
        return failureCount > 0;
    }

    public boolean isFullySuccessful() {
        return failureCount == 0 && skippedCount == 0 && successCount == totalShards;
    }

    public List<ShardMigrationResult> getFailedShards() {
        return results.stream()
                .filter(ShardMigrationResult::isFailed)
                .collect(Collectors.toList());
    }

    public Map<MigrationStatus, Long> getStatusSummary() {
        return results.stream()
                .collect(Collectors.groupingBy(
                        ShardMigrationResult::getStatus,
                        Collectors.counting()
                ));
    }

    public double getSuccessRate() {
        if (totalShards == 0) return 0.0;
        return (double) successCount / totalShards * 100;
    }
}
