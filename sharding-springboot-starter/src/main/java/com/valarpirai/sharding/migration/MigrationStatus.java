package com.valarpirai.sharding.migration;

/**
 * Status of a migration operation.
 */
public enum MigrationStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    SKIPPED,
    ROLLED_BACK
}
