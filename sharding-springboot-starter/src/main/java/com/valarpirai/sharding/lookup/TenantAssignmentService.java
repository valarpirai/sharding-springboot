package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.config.ShardConfig;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.exception.ShardLookupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tenant-to-shard mapping operations: lookups, assignment, and distribution queries.
 * All reads and writes go through ITenantShardMappingRepo.
 */
@Component
public class TenantAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(TenantAssignmentService.class);

    private final ITenantShardMappingRepo mappingRepo;
    private final ShardConfigService shardConfigService;

    public TenantAssignmentService(ITenantShardMappingRepo mappingRepo, ShardConfigService shardConfigService) {
        this.mappingRepo = mappingRepo;
        this.shardConfigService = shardConfigService;
    }

    public Optional<String> getCurrentTenantShard() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return Optional.empty();
        return getShardForTenant(tenantId);
    }

    public Optional<String> getShardForTenant(Long tenantId) {
        if (tenantId == null) return Optional.empty();
        return mappingRepo.findShardByTenantId(tenantId)
                .filter(TenantShardMapping::isActive)
                .map(TenantShardMapping::getShardId);
    }

    public Optional<TenantShardMapping> getTenantMapping(Long tenantId) {
        if (tenantId == null) return Optional.empty();
        return mappingRepo.findShardByTenantId(tenantId);
    }

    public boolean tenantExists(Long tenantId) {
        return tenantId != null && getTenantMapping(tenantId).isPresent();
    }

    public String getLatestShard() {
        return mappingRepo.getLatestShardId();
    }

    public TenantShardMapping assignTenantToLatestShard(Long tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant ID cannot be null");

        String latestShardId = getLatestShard();
        ShardConfig config = shardConfigService.getShardConfig(latestShardId)
                .orElseThrow(() -> new ShardLookupException("Latest shard configuration not found: " + latestShardId));

        logger.info("Assigning tenant {} to latest shard: {}", tenantId, latestShardId);
        return mappingRepo.createMapping(tenantId, latestShardId, config.getRegion());
    }

    public TenantShardMapping assignTenantToShard(Long tenantId, String shardId) {
        if (tenantId == null || shardId == null)
            throw new IllegalArgumentException("Tenant ID and shard ID cannot be null");
        if (!shardConfigService.isShardConfigured(shardId))
            throw new ShardLookupException("Shard is not configured: " + shardId);

        ShardConfig config = shardConfigService.getShardConfig(shardId).orElseThrow();
        logger.info("Assigning tenant {} to shard: {}", tenantId, shardId);
        return mappingRepo.createMapping(tenantId, shardId, config.getRegion());
    }

    @Deprecated
    public boolean moveTenantToShard(Long tenantId, String newShardId) {
        if (tenantId == null || newShardId == null)
            throw new IllegalArgumentException("Tenant ID and shard ID cannot be null");
        if (!shardConfigService.isShardConfigured(newShardId))
            throw new ShardLookupException("Target shard is not configured: " + newShardId);

        ShardConfig config = shardConfigService.getShardConfig(newShardId).orElseThrow();
        logger.info("Moving tenant {} to shard: {}", tenantId, newShardId);
        return mappingRepo.updateMapping(tenantId, newShardId, config.getRegion(), ShardConfig.STATUS_ACTIVE);
    }

    public Map<String, Long> getTenantDistribution() {
        return mappingRepo.findAllMappings().stream()
                .filter(TenantShardMapping::isActive)
                .collect(Collectors.groupingBy(TenantShardMapping::getShardId, Collectors.counting()));
    }

    public List<Long> getTenantsInShard(String shardId) {
        if (shardId == null) return List.of();
        return mappingRepo.findAllMappings().stream()
                .filter(m -> shardId.equals(m.getShardId()))
                .filter(TenantShardMapping::isActive)
                .map(TenantShardMapping::getTenantId)
                .collect(Collectors.toList());
    }

    public ShardStatistics getShardStatistics() {
        List<TenantShardMapping> all = mappingRepo.findAllMappings();
        return new ShardStatistics(
                shardConfigService.getAllShardIds().size(),
                shardConfigService.getActiveShardIds().size(),
                all.size(),
                getTenantDistribution());
    }
}
