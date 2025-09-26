package com.valarpirai.sharding.context;

import javax.sql.DataSource;

/**
 * Tenant information holder with pre-resolved shard information.
 * This record stores complete shard context resolved upfront in the filter layer
 * to avoid dynamic shard lookups during query execution.
 *
 * @param tenantId the tenant identifier
 * @param shardId the shard identifier where tenant data resides
 * @param readOnlyMode whether operations should be read-only
 * @param shardDataSource pre-resolved DataSource for the tenant's shard
 */
public record TenantInfo(
    Long tenantId,
    String shardId,
    boolean readOnlyMode,
    DataSource shardDataSource
) {

    /**
     * Constructor that enforces shard DataSource requirement.
     * This prevents creation of incomplete TenantInfo without shard DataSource.
     */
    public TenantInfo {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (shardId == null) {
            throw new IllegalArgumentException("Shard ID cannot be null - TenantInfo must be created with complete shard information");
        }
        if (shardDataSource == null) {
            throw new IllegalArgumentException("Shard DataSource cannot be null - TenantInfo must be created with pre-resolved DataSource");
        }
    }

    /**
     * Create TenantInfo for read-write operations.
     */
    public static TenantInfo create(Long tenantId, String shardId, DataSource shardDataSource) {
        return new TenantInfo(tenantId, shardId, false, shardDataSource);
    }

    /**
     * Create TenantInfo for read-only operations.
     */
    public static TenantInfo createReadOnly(Long tenantId, String shardId, DataSource shardDataSource) {
        return new TenantInfo(tenantId, shardId, true, shardDataSource);
    }

    /**
     * Create a copy of this TenantInfo with read-only mode toggled.
     */
    public TenantInfo withReadOnlyMode(boolean readOnlyMode) {
        return new TenantInfo(tenantId, shardId, readOnlyMode, shardDataSource);
    }
}
