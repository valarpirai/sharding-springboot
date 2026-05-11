package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.config.ShardConfig;
import com.valarpirai.sharding.context.TenantInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Convenience facade over ShardConfigService, TenantAssignmentService, and
 * ShardResolutionService. Keeps the public API stable while the three focused
 * services each carry a single responsibility.
 */
@Component
public class ShardingFacade {

    private final ShardConfigService shardConfigService;
    private final TenantAssignmentService tenantAssignmentService;
    private final ShardResolutionService shardResolutionService;

    public ShardingFacade(ShardConfigService shardConfigService,
                      TenantAssignmentService tenantAssignmentService,
                      ShardResolutionService shardResolutionService) {
        this.shardConfigService = shardConfigService;
        this.tenantAssignmentService = tenantAssignmentService;
        this.shardResolutionService = shardResolutionService;
    }

    // --- ShardConfigService delegates ---

    public Optional<ShardConfig> getShardConfig(String shardId) {
        return shardConfigService.getShardConfig(shardId);
    }

    public boolean isShardConfigured(String shardId) {
        return shardConfigService.isShardConfigured(shardId);
    }

    public Set<String> getAllShardIds() {
        return shardConfigService.getAllShardIds();
    }

    public Set<String> getActiveShardIds() {
        return shardConfigService.getActiveShardIds();
    }

    // --- TenantAssignmentService delegates ---

    public Optional<String> getCurrentTenantShard() {
        return tenantAssignmentService.getCurrentTenantShard();
    }

    public Optional<String> getShardForTenant(Long tenantId) {
        return tenantAssignmentService.getShardForTenant(tenantId);
    }

    public Optional<TenantShardMapping> getTenantMapping(Long tenantId) {
        return tenantAssignmentService.getTenantMapping(tenantId);
    }

    public boolean tenantExists(Long tenantId) {
        return tenantAssignmentService.tenantExists(tenantId);
    }

    public String getLatestShard() {
        return tenantAssignmentService.getLatestShard();
    }

    public TenantShardMapping assignTenantToLatestShard(Long tenantId) {
        return tenantAssignmentService.assignTenantToLatestShard(tenantId);
    }

    public TenantShardMapping assignTenantToShard(Long tenantId, String shardId) {
        return tenantAssignmentService.assignTenantToShard(tenantId, shardId);
    }

    @Deprecated
    public boolean moveTenantToShard(Long tenantId, String newShardId) {
        return tenantAssignmentService.moveTenantToShard(tenantId, newShardId);
    }

    public Map<String, Long> getTenantDistribution() {
        return tenantAssignmentService.getTenantDistribution();
    }

    public List<Long> getTenantsInShard(String shardId) {
        return tenantAssignmentService.getTenantsInShard(shardId);
    }

    public ShardStatistics getShardStatistics() {
        return tenantAssignmentService.getShardStatistics();
    }

    // --- ShardResolutionService delegates ---

    public Optional<TenantInfo> resolveTenantInfo(Long tenantId, boolean readOnly) {
        return shardResolutionService.resolveTenantInfo(tenantId, readOnly);
    }

    public boolean resolveAndSetTenantContext(Long tenantId, boolean readOnly) {
        return shardResolutionService.resolveAndSetTenantContext(tenantId, readOnly);
    }
}
