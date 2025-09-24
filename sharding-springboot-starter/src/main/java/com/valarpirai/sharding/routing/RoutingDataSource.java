package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.annotation.ShardedEntity;
import com.valarpirai.sharding.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DataSource implementation that routes connections based on tenant context.
 * Integrates with Spring's DataSource abstraction and JDBC template.
 */
public class RoutingDataSource extends AbstractDataSource {

    private static final Logger logger = LoggerFactory.getLogger(RoutingDataSource.class);

    private final ConnectionRouter connectionRouter;
    private final ThreadLocal<Boolean> shardedEntityContext = new ThreadLocal<>();

    public RoutingDataSource(ConnectionRouter connectionRouter) {
        this.connectionRouter = connectionRouter;
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataSource targetDataSource = determineTargetDataSource();
        Connection connection = targetDataSource.getConnection();

        logger.debug("Obtained connection from target DataSource for tenant: {}",
                    TenantContext.getCurrentTenantId());

        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        DataSource targetDataSource = determineTargetDataSource();
        Connection connection = targetDataSource.getConnection(username, password);

        logger.debug("Obtained connection with credentials from target DataSource for tenant: {}",
                    TenantContext.getCurrentTenantId());

        return connection;
    }

    /**
     * Set context for sharded entity operations.
     * This method should be called before database operations on sharded entities.
     *
     * @param forShardedEntity true if the operation is for a sharded entity
     */
    public void setShardedEntityContext(boolean forShardedEntity) {
        shardedEntityContext.set(forShardedEntity);
        logger.trace("Set sharded entity context: {}", forShardedEntity);
    }

    /**
     * Clear the sharded entity context.
     */
    public void clearShardedEntityContext() {
        shardedEntityContext.remove();
        logger.trace("Cleared sharded entity context");
    }

    /**
     * Check if current operation is for a sharded entity.
     *
     * @return true if operation is for sharded entity
     */
    public boolean isShardedEntityContext() {
        Boolean context = shardedEntityContext.get();
        return context != null ? context : false;
    }

    /**
     * Determine the target DataSource based on current context.
     *
     * @return the appropriate DataSource
     * @throws SQLException if routing fails
     */
    protected DataSource determineTargetDataSource() throws SQLException {
        try {
            boolean forShardedEntity = isShardedEntityContext();

            if (logger.isDebugEnabled()) {
                Long tenantId = TenantContext.getCurrentTenantId();
                boolean readOnly = TenantContext.isReadOnlyMode();
                logger.debug("Routing connection - tenant: {}, sharded: {}, readOnly: {}",
                           tenantId, forShardedEntity, readOnly);
            }

            return connectionRouter.routeDataSource(forShardedEntity);

        } catch (RoutingException e) {
            logger.error("Failed to route connection: {}", e.getMessage());
            throw new SQLException("Connection routing failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during connection routing", e);
            throw new SQLException("Unexpected routing error: " + e.getMessage(), e);
        }
    }

    /**
     * Execute an operation with explicit sharded entity context.
     *
     * @param forShardedEntity whether this is for a sharded entity
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws SQLException if the operation fails
     */
    public <T> T executeWithContext(boolean forShardedEntity, SqlOperation<T> operation) throws SQLException {
        boolean previousContext = isShardedEntityContext();
        try {
            setShardedEntityContext(forShardedEntity);
            return operation.execute(this);
        } finally {
            if (previousContext) {
                setShardedEntityContext(previousContext);
            } else {
                clearShardedEntityContext();
            }
        }
    }

    /**
     * Execute an operation for a sharded entity with tenant context.
     *
     * @param tenantId the tenant identifier
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws SQLException if the operation fails
     */
    public <T> T executeForTenant(Long tenantId, SqlOperation<T> operation) throws SQLException {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            try {
                return executeWithContext(true, operation);
            } catch (SQLException e) {
                throw new RuntimeException("SQL operation failed for tenant: " + tenantId, e);
            }
        });
    }

    /**
     * Get connection router statistics.
     *
     * @return routing statistics
     */
    public ConnectionRouter.RoutingStatistics getRoutingStatistics() {
        return connectionRouter.getRoutingStatistics();
    }

    /**
     * Check if a specific shard is available for routing.
     *
     * @param shardId the shard identifier
     * @return true if shard is available
     */
    public boolean isShardAvailable(String shardId) {
        return connectionRouter.isShardAvailable(shardId);
    }

    /**
     * Get the underlying connection router.
     *
     * @return the connection router
     */
    public ConnectionRouter getConnectionRouter() {
        return connectionRouter;
    }

    /**
     * Functional interface for SQL operations.
     */
    @FunctionalInterface
    public interface SqlOperation<T> {
        T execute(DataSource dataSource) throws SQLException;
    }
}