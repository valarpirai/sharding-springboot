package com.valarpirai.sharding.lookup;

import java.util.List;

/**
 * Optional caching operations for tenant-shard mappings.
 * Default no-op implementations allow implementations to opt in selectively.
 */
public interface ITenantShardCacheRepo {

    default void evictFromCache(Long tenantId) {}

    default void clearCache() {}

    default void warmUpCache(List<Long> tenantIds) {}
}
