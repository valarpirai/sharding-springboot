package com.valarpirai.sharding.lookup;

import java.util.List;
import java.util.Optional;

/**
 * Read-only contract for tenant-to-shard mapping lookups.
 * Consumers that never write (iterators, routing, resolution) should depend on
 * this interface rather than the full {@link ITenantShardMappingRepo}.
 */
public interface ITenantShardMappingReadRepo {

    Optional<TenantShardMapping> findShardByTenantId(Long tenantId);

    List<TenantShardMapping> findAllMappings();

    String getLatestShardId();
}
