package com.valarpirai.sharding.lookup;

/**
 * Full tenant-shard mapping contract combining read, write, and optional cache operations.
 *
 * Consumers that only read should depend on {@link ITenantShardMappingReadRepo}.
 * Consumers that need writes depend on this interface.
 * Custom implementations may choose not to implement {@link ITenantShardCacheRepo}
 * methods — the default no-ops in that interface handle the gap.
 */
public interface ITenantShardMappingRepo
        extends ITenantShardMappingReadRepo,
                ITenantShardMappingWriteRepo,
                ITenantShardCacheRepo {
}
