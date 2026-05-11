package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.exception.RoutingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure infrastructure component: given a shard ID and read/write flag, returns
 * the appropriate DataSource (master or replica). Has no knowledge of tenants
 * or shard mappings — that belongs to ShardResolutionService.
 */
@Component
public class ShardDataSourceRouter {

    private static final Logger logger = LoggerFactory.getLogger(ShardDataSourceRouter.class);

    private final Map<String, ShardDataSources> shardDataSources;
    private final DataSource globalDataSource;

    public ShardDataSourceRouter(Map<String, ShardDataSources> shardDataSources,
                                 DataSource globalDataSource) {
        this.shardDataSources = new ConcurrentHashMap<>(shardDataSources);
        this.globalDataSource = globalDataSource;
    }

    /**
     * Returns the global DataSource for non-sharded entity operations.
     */
    public DataSource getGlobalDataSource() {
        return globalDataSource;
    }

    /**
     * Returns the appropriate DataSource for a given shard and operation type.
     *
     * @param shardId  the shard identifier
     * @param readOnly true to select a replica, false for master
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
     * Add or update shard data sources (for dynamic shard management).
     */
    public void updateShardDataSources(String shardId, ShardDataSources dataSources) {
        logger.info("Updating data sources for shard: {}", shardId);
        this.shardDataSources.put(shardId, dataSources);
    }

    /**
     * Remove shard data sources (for shard decommissioning).
     */
    public void removeShardDataSources(String shardId) {
        logger.info("Removing data sources for shard: {}", shardId);
        ShardDataSources removed = this.shardDataSources.remove(shardId);
        if (removed != null) {
            removed.close();
        }
    }

    /**
     * Check if routing is available for a specific shard.
     */
    public boolean isShardAvailable(String shardId) {
        return shardDataSources.containsKey(shardId);
    }

    /**
     * Get routing statistics for monitoring.
     */
    public RoutingStatistics getRoutingStatistics() {
        int totalShards = shardDataSources.size();
        int shardsWithReplicas = (int) shardDataSources.values().stream()
                .filter(ShardDataSources::hasReplicas)
                .count();
        return new RoutingStatistics(totalShards, shardsWithReplicas);
    }

    private DataSource selectDataSource(ShardDataSources dataSources, boolean readOnly, String shardId) {
        if (readOnly && dataSources.hasReplicas()) {
            logger.debug("Selected replica for read operation on shard: {}", shardId);
            return dataSources.selectReplica();
        }
        if (readOnly) {
            logger.debug("No replicas available for shard {}, falling back to master", shardId);
        } else {
            logger.debug("Selected master for write operation on shard: {}", shardId);
        }
        return dataSources.getMaster();
    }

    public record RoutingStatistics(int totalShards, int shardsWithReplicas) {
        public int getShardsWithoutReplicas() {
            return totalShards - shardsWithReplicas;
        }
    }
}
