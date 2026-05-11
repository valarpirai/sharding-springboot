package com.valarpirai.sharding.lookup;

/**
 * Write contract for tenant-to-shard mapping mutations.
 */
public interface ITenantShardMappingWriteRepo {

    TenantShardMapping createMapping(Long tenantId, String shardId, String region);

    TenantShardMapping createMapping(Long tenantId, String shardId, String region, String status);

    boolean updateMapping(Long tenantId, String newShardId, String newRegion, String newStatus);
}
