package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.valarpirai.sharding.routing.ShardAwareDataSourceDelegate;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Resolves a fully-populated TenantInfo (tenant ID + shard ID + DataSource)
 * and manages TenantContext for the current thread.
 */
@Component
public class ShardResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(ShardResolutionService.class);

    private final ITenantShardMappingReadRepo mappingRepo;
    private final ShardAwareDataSourceDelegate shardDelegate;

    public ShardResolutionService(ITenantShardMappingReadRepo mappingRepo,
                                  ShardAwareDataSourceDelegate shardDelegate) {
        this.mappingRepo = mappingRepo;
        this.shardDelegate = shardDelegate;
    }

    /**
     * Resolves complete TenantInfo including the pre-resolved shard DataSource.
     *
     * @param tenantId the tenant identifier
     * @param readOnly true to select a replica DataSource, false for master
     * @return populated TenantInfo, or empty if no active mapping exists
     */
    public Optional<TenantInfo> resolveTenantInfo(Long tenantId, boolean readOnly) {
        if (tenantId == null) {
            logger.debug("Cannot resolve shard info for null tenant ID");
            return Optional.empty();
        }
        try {
            Optional<TenantShardMapping> mappingOpt = mappingRepo.findShardByTenantId(tenantId);
            if (mappingOpt.isEmpty() || !mappingOpt.get().isActive()) {
                logger.warn("No active shard mapping found for tenant: {}", tenantId);
                return Optional.empty();
            }

            String shardId = mappingOpt.get().getShardId();
            DataSource dataSource = shardDelegate.getShardDataSource(shardId, readOnly);
            TenantInfo tenantInfo = new TenantInfo(tenantId, shardId, readOnly, dataSource);

            logger.debug("Resolved tenant info — tenant: {}, shard: {}, readOnly: {}",
                    tenantId, shardId, readOnly);
            return Optional.of(tenantInfo);

        } catch (Exception e) {
            logger.error("Failed to resolve shard info for tenant {}: {}", tenantId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Resolves TenantInfo and sets it on TenantContext for the current thread.
     * Intended for use in servlet filters and controllers.
     *
     * @return true if context was set, false if no active mapping was found
     */
    public boolean resolveAndSetTenantContext(Long tenantId, boolean readOnly) {
        return resolveTenantInfo(tenantId, readOnly)
                .map(info -> {
                    TenantContext.setTenantInfo(info);
                    logger.debug("Set tenant context — tenant: {}, shard: {}", tenantId, info.shardId());
                    return true;
                })
                .orElse(false);
    }
}
