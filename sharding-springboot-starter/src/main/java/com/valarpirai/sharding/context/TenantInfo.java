package com.valarpirai.sharding.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.sql.DataSource;

/**
 * Tenant information holder with pre-resolved shard information.
 * This class stores complete shard context resolved upfront in the filter layer
 * to avoid dynamic shard lookups during query execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInfo {

    private Long tenantId;
    private String shardId;
    private boolean readOnlyMode;

    /**
     * Pre-resolved DataSource for the tenant's shard.
     * This allows RoutingDataSource to directly use the resolved DataSource
     * without performing additional lookups during query execution.
     */
    private DataSource shardDataSource;

    // Legacy constructor for backward compatibility
    public TenantInfo(Long tenantId, String shardId, boolean readOnlyMode) {
        this.tenantId = tenantId;
        this.shardId = shardId;
        this.readOnlyMode = readOnlyMode;
        this.shardDataSource = null; // Not pre-resolved
    }
}
