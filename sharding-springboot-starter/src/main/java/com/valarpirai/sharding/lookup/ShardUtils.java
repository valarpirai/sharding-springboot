package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.config.ShardConfigProperties;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import com.valarpirai.sharding.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for shard-related operations and lookups.
 * Provides convenience methods for shard management and tenant routing.
 */
@Component
public class ShardUtils {

    private static final Logger logger = LoggerFactory.getLogger(ShardUtils.class);

    private final ShardLookupService shardLookupService;
    private final ShardingConfigProperties shardingConfig;

    public ShardUtils(ShardLookupService shardLookupService, ShardingConfigProperties shardingConfig) {
        this.shardLookupService = shardLookupService;
        this.shardingConfig = shardingConfig;
    }

    /**
     * Get the shard ID for the current tenant in context.
     *
     * @return the shard ID or empty if no tenant context or mapping found
     */
    public Optional<String> getCurrentTenantShard() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            logger.debug("No tenant context available");
            return Optional.empty();
        }
        return getShardForTenant(tenantId);
    }

    /**
     * Get the shard ID for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return the shard ID or empty if not found
     */
    public Optional<String> getShardForTenant(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }

        return shardLookupService.findShardByTenantId(tenantId)
                .filter(TenantShardMapping::isActive)
                .map(TenantShardMapping::getShardId);
    }

    /**
     * Get the complete mapping for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return the mapping or empty if not found
     */
    public Optional<TenantShardMapping> getTenantMapping(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return shardLookupService.findShardByTenantId(tenantId);
    }

    /**
     * Get the latest shard ID for new tenant signups.
     *
     * @return the latest shard ID
     * @throws ShardLookupException if no shard is marked as latest
     */
    public String getLatestShard() {
        return shardLookupService.getLatestShardId();
    }

    /**
     * Get the configuration for a specific shard.
     *
     * @param shardId the shard identifier
     * @return the shard configuration or empty if not found
     */
    public Optional<ShardConfigProperties> getShardConfig(String shardId) {
        if (shardId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(shardingConfig.getShards().get(shardId));
    }

    /**
     * Check if a shard is configured.
     *
     * @param shardId the shard identifier
     * @return true if shard is configured
     */
    public boolean isShardConfigured(String shardId) {
        return shardId != null && shardingConfig.getShards().containsKey(shardId);
    }

    /**
     * Get all configured shard IDs.
     *
     * @return set of shard IDs
     */
    public Set<String> getAllShardIds() {
        return shardingConfig.getShards().keySet();
    }

    /**
     * Get all active shard IDs (configured and with ACTIVE status).
     *
     * @return set of active shard IDs
     */
    public Set<String> getActiveShardIds() {
        return shardingConfig.getShards().entrySet().stream()
                .filter(entry -> "ACTIVE".equalsIgnoreCase(entry.getValue().getStatus()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Get tenant distribution across shards.
     *
     * @return map of shard ID to tenant count
     */
    public Map<String, Long> getTenantDistribution() {
        List<TenantShardMapping> allMappings = shardLookupService.findAllMappings();
        return allMappings.stream()
                .filter(TenantShardMapping::isActive)
                .collect(Collectors.groupingBy(
                        TenantShardMapping::getShardId,
                        Collectors.counting()
                ));
    }

    /**
     * Get all tenants in a specific shard.
     *
     * @param shardId the shard identifier
     * @return list of tenant IDs in the shard
     */
    public List<Long> getTenantsInShard(String shardId) {
        if (shardId == null) {
            return List.of();
        }

        List<TenantShardMapping> allMappings = shardLookupService.findAllMappings();
        return allMappings.stream()
                .filter(mapping -> shardId.equals(mapping.getShardId()))
                .filter(TenantShardMapping::isActive)
                .map(TenantShardMapping::getTenantId)
                .collect(Collectors.toList());
    }

    /**
     * Assign a new tenant to the latest shard.
     *
     * @param tenantId the new tenant identifier
     * @return the created mapping
     * @throws ShardLookupException if assignment fails
     */
    public TenantShardMapping assignTenantToLatestShard(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        String latestShardId = getLatestShard();
        ShardConfigProperties shardConfig = getShardConfig(latestShardId)
                .orElseThrow(() -> new ShardLookupException("Latest shard configuration not found: " + latestShardId));

        logger.info("Assigning tenant {} to latest shard: {}", tenantId, latestShardId);
        return shardLookupService.createMapping(tenantId, latestShardId, shardConfig.getRegion());
    }

    /**
     * Assign a tenant to a specific shard.
     *
     * @param tenantId the tenant identifier
     * @param shardId the target shard identifier
     * @return the created mapping
     * @throws ShardLookupException if assignment fails
     */
    public TenantShardMapping assignTenantToShard(Long tenantId, String shardId) {
        if (tenantId == null || shardId == null) {
            throw new IllegalArgumentException("Tenant ID and shard ID cannot be null");
        }

        if (!isShardConfigured(shardId)) {
            throw new ShardLookupException("Shard is not configured: " + shardId);
        }

        ShardConfigProperties shardConfig = getShardConfig(shardId).orElseThrow();
        logger.info("Assigning tenant {} to shard: {}", tenantId, shardId);
        return shardLookupService.createMapping(tenantId, shardId, shardConfig.getRegion());
    }

    /**
     * Move a tenant to a different shard.
     *
     * @param tenantId the tenant identifier
     * @param newShardId the new shard identifier
     * @return true if moved successfully
     * @throws ShardLookupException if move fails
     */
    public boolean moveTenantToShard(Long tenantId, String newShardId) {
        if (tenantId == null || newShardId == null) {
            throw new IllegalArgumentException("Tenant ID and shard ID cannot be null");
        }

        if (!isShardConfigured(newShardId)) {
            throw new ShardLookupException("Target shard is not configured: " + newShardId);
        }

        ShardConfigProperties shardConfig = getShardConfig(newShardId).orElseThrow();
        logger.info("Moving tenant {} to shard: {}", tenantId, newShardId);
        return shardLookupService.updateMapping(tenantId, newShardId, shardConfig.getRegion(), "ACTIVE");
    }

    /**
     * Check if a tenant exists in the shard mapping.
     *
     * @param tenantId the tenant identifier
     * @return true if tenant has a shard mapping
     */
    public boolean tenantExists(Long tenantId) {
        return tenantId != null && getTenantMapping(tenantId).isPresent();
    }

    /**
     * Get shard statistics.
     *
     * @return shard statistics summary
     */
    public ShardStatistics getShardStatistics() {
        List<TenantShardMapping> allMappings = shardLookupService.findAllMappings();
        Map<String, Long> distribution = getTenantDistribution();

        return new ShardStatistics(
                shardingConfig.getShards().size(),
                getActiveShardIds().size(),
                allMappings.size(),
                distribution
        );
    }

    /**
     * Statistics summary for shards.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ShardStatistics {
        private final int totalShards;
        private final int activeShards;
        private final int totalTenants;
        private final Map<String, Long> tenantDistribution;
    }
}