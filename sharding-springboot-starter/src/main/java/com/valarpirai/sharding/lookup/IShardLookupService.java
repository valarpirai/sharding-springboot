package com.valarpirai.sharding.lookup;

import java.util.List;
import java.util.Optional;

/**
 * Interface for shard lookup operations.
 * Custom implementations can be provided to override the default behavior.
 */
public interface IShardLookupService {

    /**
     * Find the shard for a given tenant ID.
     *
     * @param tenantId the tenant identifier
     * @return the tenant-shard mapping if found
     */
    Optional<TenantShardMapping> findShardByTenantId(Long tenantId);

    /**
     * Get all tenant-shard mappings.
     *
     * @return list of all mappings
     */
    List<TenantShardMapping> findAllMappings();

    /**
     * Create a new tenant-shard mapping.
     *
     * @param tenantId the tenant ID
     * @param shardId the shard ID
     * @param region the region
     * @return the created mapping
     */
    TenantShardMapping createMapping(Long tenantId, String shardId, String region);

    /**
     * Create a new tenant-shard mapping with specified status.
     *
     * @param tenantId the tenant ID
     * @param shardId the shard ID
     * @param region the region
     * @param status the shard status
     * @return the created mapping
     */
    TenantShardMapping createMapping(Long tenantId, String shardId, String region, String status);

    /**
     * Update an existing tenant-shard mapping.
     *
     * @param tenantId the tenant ID
     * @param newShardId the new shard ID
     * @param newRegion the new region
     * @param newStatus the new status
     * @return true if updated successfully
     */
    boolean updateMapping(Long tenantId, String newShardId, String newRegion, String newStatus);

    /**
     * Get the latest shard configured for new tenant signups.
     *
     * @return the latest shard ID
     */
    String getLatestShardId();

    /**
     * Evict tenant-shard mapping from cache (if caching is supported).
     *
     * @param tenantId the tenant ID to evict from cache
     */
    default void evictFromCache(Long tenantId) {
        // Default implementation does nothing
    }

    /**
     * Clear all cached tenant-shard mappings (if caching is supported).
     */
    default void clearCache() {
        // Default implementation does nothing
    }

    /**
     * Warm up cache by loading frequently used tenant mappings (if caching is supported).
     *
     * @param tenantIds list of tenant IDs to pre-load
     */
    default void warmUpCache(List<Long> tenantIds) {
        // Default implementation does nothing
    }
}