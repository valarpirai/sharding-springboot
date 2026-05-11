package com.valarpirai.sharding.lookup;

import java.util.Map;

/**
 * Snapshot of shard distribution and tenant counts.
 */
public record ShardStatistics(
        int totalShards,
        int activeShards,
        int totalTenants,
        Map<String, Long> tenantDistribution) {
}
