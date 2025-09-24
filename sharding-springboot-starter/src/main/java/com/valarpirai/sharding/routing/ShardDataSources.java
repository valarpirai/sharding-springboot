package com.valarpirai.sharding.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Container for a shard's master and replica DataSources.
 * Handles replica selection with load balancing strategies.
 */
public class ShardDataSources {

    private static final Logger logger = LoggerFactory.getLogger(ShardDataSources.class);

    private final String shardId;
    private final DataSource master;
    private final List<DataSource> replicas;
    private final AtomicInteger replicaIndex;
    private final ReplicaSelectionStrategy selectionStrategy;

    public ShardDataSources(String shardId, DataSource master) {
        this(shardId, master, new ArrayList<>(), ReplicaSelectionStrategy.ROUND_ROBIN);
    }

    public ShardDataSources(String shardId, DataSource master, List<DataSource> replicas) {
        this(shardId, master, replicas, ReplicaSelectionStrategy.ROUND_ROBIN);
    }

    public ShardDataSources(String shardId, DataSource master, List<DataSource> replicas,
                          ReplicaSelectionStrategy selectionStrategy) {
        this.shardId = shardId;
        this.master = master;
        this.replicas = new ArrayList<>(replicas);
        this.replicaIndex = new AtomicInteger(0);
        this.selectionStrategy = selectionStrategy;

        logger.debug("Created ShardDataSources for shard {} with {} replicas using {} strategy",
                    shardId, replicas.size(), selectionStrategy);
    }

    /**
     * Get the master DataSource for write operations.
     *
     * @return the master DataSource
     */
    public DataSource getMaster() {
        return master;
    }

    /**
     * Get all replica DataSources.
     *
     * @return list of replica DataSources
     */
    public List<DataSource> getReplicas() {
        return new ArrayList<>(replicas);
    }

    /**
     * Check if this shard has any replica DataSources.
     *
     * @return true if replicas are available
     */
    public boolean hasReplicas() {
        return !replicas.isEmpty();
    }

    /**
     * Get the number of replica DataSources.
     *
     * @return replica count
     */
    public int getReplicaCount() {
        return replicas.size();
    }

    /**
     * Select a replica DataSource using the configured strategy.
     * If no replicas are available, returns the master.
     *
     * @return selected replica DataSource or master if no replicas
     */
    public DataSource selectReplica() {
        if (replicas.isEmpty()) {
            logger.debug("No replicas available for shard {}, using master", shardId);
            return master;
        }

        switch (selectionStrategy) {
            case ROUND_ROBIN:
                return selectReplicaRoundRobin();
            case RANDOM:
                return selectReplicaRandom();
            case FIRST_AVAILABLE:
                return selectReplicaFirstAvailable();
            default:
                logger.warn("Unknown replica selection strategy: {}, falling back to round robin", selectionStrategy);
                return selectReplicaRoundRobin();
        }
    }

    /**
     * Add a replica DataSource.
     *
     * @param replica the replica DataSource to add
     */
    public void addReplica(DataSource replica) {
        if (replica != null) {
            replicas.add(replica);
            logger.debug("Added replica to shard {}, total replicas: {}", shardId, replicas.size());
        }
    }

    /**
     * Remove a replica DataSource.
     *
     * @param replica the replica DataSource to remove
     * @return true if replica was removed
     */
    public boolean removeReplica(DataSource replica) {
        boolean removed = replicas.remove(replica);
        if (removed) {
            logger.debug("Removed replica from shard {}, remaining replicas: {}", shardId, replicas.size());
        }
        return removed;
    }

    /**
     * Get the shard identifier.
     *
     * @return the shard ID
     */
    public String getShardId() {
        return shardId;
    }

    /**
     * Close all DataSources if they support it.
     */
    public void close() {
        logger.info("Closing data sources for shard: {}", shardId);

        // Try to close master if it supports AutoCloseable
        closeDataSource(master, "master");

        // Try to close all replicas
        for (int i = 0; i < replicas.size(); i++) {
            closeDataSource(replicas.get(i), "replica-" + i);
        }
    }

    /**
     * Round-robin replica selection.
     */
    private DataSource selectReplicaRoundRobin() {
        int index = replicaIndex.getAndIncrement() % replicas.size();
        DataSource selected = replicas.get(index);
        logger.trace("Selected replica {} (index {}) for shard {}", index, index, shardId);
        return selected;
    }

    /**
     * Random replica selection.
     */
    private DataSource selectReplicaRandom() {
        int index = (int) (Math.random() * replicas.size());
        DataSource selected = replicas.get(index);
        logger.trace("Selected random replica {} for shard {}", index, shardId);
        return selected;
    }

    /**
     * First available replica selection (always use first replica).
     */
    private DataSource selectReplicaFirstAvailable() {
        DataSource selected = replicas.get(0);
        logger.trace("Selected first available replica for shard {}", shardId);
        return selected;
    }

    /**
     * Attempt to close a DataSource if it implements AutoCloseable.
     */
    private void closeDataSource(DataSource dataSource, String type) {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
                logger.debug("Closed {} DataSource for shard {}", type, shardId);
            } catch (Exception e) {
                logger.warn("Failed to close {} DataSource for shard {}: {}", type, shardId, e.getMessage());
            }
        } else {
            logger.debug("{} DataSource for shard {} does not support auto-closing", type, shardId);
        }
    }

    @Override
    public String toString() {
        return "ShardDataSources{" +
               "shardId='" + shardId + '\'' +
               ", hasReplicas=" + hasReplicas() +
               ", replicaCount=" + getReplicaCount() +
               ", selectionStrategy=" + selectionStrategy +
               '}';
    }

    /**
     * Replica selection strategies.
     */
    public enum ReplicaSelectionStrategy {
        /**
         * Round-robin selection across all replicas.
         */
        ROUND_ROBIN,

        /**
         * Random selection from available replicas.
         */
        RANDOM,

        /**
         * Always use the first available replica.
         */
        FIRST_AVAILABLE
    }
}