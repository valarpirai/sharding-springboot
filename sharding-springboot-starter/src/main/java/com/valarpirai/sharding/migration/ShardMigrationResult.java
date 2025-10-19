package com.valarpirai.sharding.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of a migration operation on a single shard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShardMigrationResult {
    private String shardId;
    private MigrationStatus status;
    private String currentVersion;
    private String targetVersion;
    private int changeSetExecuted;
    private long executionTimeMs;
    private Instant startTime;
    private Instant endTime;
    private String errorMessage;
    private String stackTrace;

    public static ShardMigrationResult success(String shardId, int changeSetExecuted,
                                              String targetVersion, long executionTimeMs) {
        return ShardMigrationResult.builder()
                .shardId(shardId)
                .status(MigrationStatus.SUCCESS)
                .changeSetExecuted(changeSetExecuted)
                .targetVersion(targetVersion)
                .executionTimeMs(executionTimeMs)
                .endTime(Instant.now())
                .build();
    }

    public static ShardMigrationResult failure(String shardId, String errorMessage, String stackTrace) {
        return ShardMigrationResult.builder()
                .shardId(shardId)
                .status(MigrationStatus.FAILED)
                .errorMessage(errorMessage)
                .stackTrace(stackTrace)
                .endTime(Instant.now())
                .build();
    }

    public static ShardMigrationResult skipped(String shardId, String reason) {
        return ShardMigrationResult.builder()
                .shardId(shardId)
                .status(MigrationStatus.SKIPPED)
                .errorMessage(reason)
                .endTime(Instant.now())
                .build();
    }

    public boolean isSuccess() {
        return status == MigrationStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == MigrationStatus.FAILED;
    }
}
