package com.valarpirai.sharding.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Tenant information holder.
 */
@Data
@AllArgsConstructor
public class TenantInfo {

    private final Long tenantId;
    private String shardId;
    private boolean readOnlyMode;
}
