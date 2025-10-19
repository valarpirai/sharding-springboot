package com.valarpirai.sharding.migration;

/**
 * Strategy for executing migrations across multiple shards.
 */
public enum MigrationStrategy {
    /**
     * Migrate shards one at a time sequentially.
     * Safest but slowest approach.
     */
    SEQUENTIAL,

    /**
     * Migrate all shards in parallel.
     * Fastest but riskiest approach.
     */
    PARALLEL,

    /**
     * Migrate shards in waves/batches.
     * Balanced approach between speed and safety.
     */
    WAVE,

    /**
     * Test migration on a canary shard first, then proceed with others.
     * Safest for production environments.
     */
    CANARY
}
