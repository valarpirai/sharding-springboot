package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.config.ShardConfigProperties;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.ShardLookupService;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes database connections based on tenant context and read/write requirements.
 * Handles shard lookup, replica selection, and connection pooling.
 */
@Component
public class ConnectionRouter {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionRouter.class);

    private final ShardLookupService shardLookupService;
    private final Map<String, ShardDataSources> shardDataSources;
    private final DataSource globalDataSource;

    public ConnectionRouter(ShardLookupService shardLookupService,
                          Map<String, ShardDataSources> shardDataSources,
                          DataSource globalDataSource) {
        this.shardLookupService = shardLookupService;
        this.shardDataSources = new ConcurrentHashMap<>(shardDataSources);
        this.globalDataSource = globalDataSource;
    }

    /**
     * Route to the appropriate DataSource based on tenant context and operation type.
     *
     * @param forShardedEntity whether this is for a sharded entity
     * @return the appropriate DataSource
     * @throws RoutingException if routing fails
     */
    public DataSource routeDataSource(boolean forShardedEntity) {
        if (!forShardedEntity) {
            // Non-sharded entities go to global database
            logger.debug("Routing to global database for non-sharded entity");
            return globalDataSource;
        }

        // For sharded entities, we need tenant context
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new RoutingException("Tenant context is required for sharded entity operations");
        }

        return routeToShardedDataSource(tenantId);
    }

    /**
     * Route to a sharded DataSource for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return the appropriate shard DataSource
     * @throws RoutingException if routing fails
     */
    public DataSource routeToShardedDataSource(Long tenantId) {
        // Look up shard for tenant
        Optional<TenantShardMapping> mappingOpt = shardLookupService.findShardByTenantId(tenantId);
        if (mappingOpt.isEmpty()) {
            throw new RoutingException("No shard mapping found for tenant: " + tenantId);
        }

        TenantShardMapping mapping = mappingOpt.get();
        if (!mapping.isActive()) {
            throw new RoutingException("Shard is not active for tenant " + tenantId + ": " + mapping.getShardStatus());
        }

        String shardId = mapping.getShardId();
        logger.debug("Routing tenant {} to shard: {}", tenantId, shardId);

        // Get shard data sources
        ShardDataSources dataSources = shardDataSources.get(shardId);
        if (dataSources == null) {
            throw new RoutingException("DataSource configuration not found for shard: " + shardId);
        }

        // Determine read vs write operation
        boolean readOnlyMode = TenantContext.isReadOnlyMode();
        return selectDataSource(dataSources, readOnlyMode, shardId);
    }

    /**
     * Get DataSource for a specific shard and operation type.
     *
     * @param shardId the shard identifier
     * @param readOnly whether this is a read-only operation
     * @return the appropriate DataSource
     */
    public DataSource getShardDataSource(String shardId, boolean readOnly) {
        ShardDataSources dataSources = shardDataSources.get(shardId);
        if (dataSources == null) {
            throw new RoutingException("DataSource configuration not found for shard: " + shardId);
        }
        return selectDataSource(dataSources, readOnly, shardId);
    }

    /**
     * Get the global DataSource for non-sharded operations.
     *
     * @return the global DataSource
     */
    public DataSource getGlobalDataSource() {
        return globalDataSource;
    }

    /**
     * Select the appropriate DataSource from shard data sources based on read/write mode.
     */
    private DataSource selectDataSource(ShardDataSources dataSources, boolean readOnlyMode, String shardId) {
        if (readOnlyMode && dataSources.hasReplicas()) {
            // Use replica for read operations
            DataSource replica = dataSources.selectReplica();
            logger.debug("Selected replica for read operation on shard: {}", shardId);
            return replica;
        } else {
            // Use master for write operations or when no replicas available
            DataSource master = dataSources.getMaster();
            if (readOnlyMode && !dataSources.hasReplicas()) {
                logger.debug("No replicas available for shard {}, using master for read operation", shardId);
            } else {
                logger.debug("Selected master for write operation on shard: {}", shardId);
            }
            return master;
        }
    }

    /**
     * Add or update shard data sources (for dynamic shard management).
     *
     * @param shardId the shard identifier
     * @param dataSources the shard data sources
     */
    public void updateShardDataSources(String shardId, ShardDataSources dataSources) {
        logger.info("Updating data sources for shard: {}", shardId);
        this.shardDataSources.put(shardId, dataSources);
    }

    /**
     * Remove shard data sources (for shard decommissioning).
     *
     * @param shardId the shard identifier
     */
    public void removeShardDataSources(String shardId) {
        logger.info("Removing data sources for shard: {}", shardId);
        ShardDataSources removed = this.shardDataSources.remove(shardId);
        if (removed != null) {
            // Close data sources if they support it
            removed.close();
        }
    }

    /**
     * Check if routing is available for a specific shard.
     *
     * @param shardId the shard identifier
     * @return true if shard data sources are configured
     */
    public boolean isShardAvailable(String shardId) {
        return shardDataSources.containsKey(shardId);
    }

    /**
     * Get routing statistics for monitoring.
     *
     * @return routing statistics
     */
    public RoutingStatistics getRoutingStatistics() {
        int totalShards = shardDataSources.size();
        int shardsWithReplicas = (int) shardDataSources.values().stream()
                .filter(ShardDataSources::hasReplicas)
                .count();

        return new RoutingStatistics(totalShards, shardsWithReplicas);
    }

    /**
     * Container for routing statistics.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RoutingStatistics {
        private final int totalShards;
        private final int shardsWithReplicas;

        public int getShardsWithoutReplicas() {
            return totalShards - shardsWithReplicas;
        }
    }
}