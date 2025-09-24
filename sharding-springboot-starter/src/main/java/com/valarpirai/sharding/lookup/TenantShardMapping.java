package com.valarpirai.sharding.lookup;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Represents a tenant to shard mapping record from the directory table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantShardMapping {

    private Long tenantId;
    private String shardId;
    private String region;
    private String shardStatus;
    private LocalDateTime createdAt;

    public TenantShardMapping(Long tenantId, String shardId, String region, String shardStatus) {
        this.tenantId = tenantId;
        this.shardId = shardId;
        this.region = region;
        this.shardStatus = shardStatus;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(shardStatus);
    }
}