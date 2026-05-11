package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import com.valarpirai.sharding.exception.RoutingException;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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

    private final ShardDataSourceRouter shardAwareDataSourceDelegate;


    // OpenTelemetry tracing via @WithSpan annotations only

    public RoutingDataSource(ShardDataSourceRouter shardAwareDataSourceDelegate) {
        this.shardAwareDataSourceDelegate = shardAwareDataSourceDelegate;
    }

    @Override
    @WithSpan("sharding.datasource.get_connection")
    public Connection getConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        TenantInfo tenantInfo = TenantContext.getTenantInfo();


        try {
            DataSource targetDataSource = determineTargetDataSource();
            Connection connection = targetDataSource.getConnection();

            // Record metrics

            logger.debug("Obtained connection from target DataSource for tenant: {}",
                        TenantContext.getCurrentTenantId());

            return connection;
        } catch (SQLException e) {
            throw e;
        }
    }

    @Override
    @WithSpan("sharding.datasource.get_connection_with_credentials")
    public Connection getConnection(String username, String password) throws SQLException {
        long startTime = System.currentTimeMillis();
        TenantInfo tenantInfo = TenantContext.getTenantInfo();


        try {
            DataSource targetDataSource = determineTargetDataSource();
            Connection connection = targetDataSource.getConnection(username, password);

            // Record metrics

            logger.debug("Obtained connection with credentials from target DataSource for tenant: {}",
                        TenantContext.getCurrentTenantId());

            return connection;
        } catch (SQLException e) {
            throw e;
        }
    }


    /**
     * Determine the target DataSource based on current context.
     * Uses pre-resolved shard information from TenantContext when available,
     * falling back to ConnectionRouter for dynamic resolution.
     *
     * @return the appropriate DataSource
     * @throws SQLException if routing fails
     */
    @WithSpan("sharding.datasource.determine_target")
    protected DataSource determineTargetDataSource() throws SQLException {
        try {
            // Check if we have pre-resolved shard information in TenantContext
            TenantInfo tenantInfo = TenantContext.getTenantInfo();

            if (logger.isDebugEnabled()) {
                Long tenantId = TenantContext.getCurrentTenantId();
                boolean readOnly = TenantContext.isReadOnlyMode();
                String shardId = tenantInfo != null ? tenantInfo.shardId() : "none";
                boolean hasPreResolvedShard = tenantInfo != null && tenantInfo.shardDataSource() != null;
                logger.debug("Routing connection - tenant: {}, readOnly: {}, shard: {}, preResolved: {}",
                           tenantId, readOnly, shardId, hasPreResolvedShard);
            }

            // Use pre-resolved shard information if available
            if (tenantInfo != null && tenantInfo.shardDataSource() != null) {
                logger.info("DATASOURCE DECISION: Using pre-resolved shard DataSource from TenantInfo");
                DataSource result = tenantInfo.shardDataSource();
                logger.info("SHARD DATASOURCE: {}", result.getClass().getSimpleName());
                return result;
            }

            // No tenant context — route to global database
            if (tenantInfo == null) {
                logger.debug("No tenant context — routing to global DataSource");
                return shardAwareDataSourceDelegate.getGlobalDataSource();
            }

            // TenantInfo present but shardDataSource not pre-resolved — programming error
            throw new RoutingException(
                "TenantInfo is set but shardDataSource is not pre-resolved for tenant " +
                tenantInfo.tenantId() + ". Call ShardingFacade.resolveAndSetTenantContext() " +
                "before accessing sharded data.");

        } catch (RoutingException e) {
            logger.error("Failed to route connection: {}", e.getMessage());
            throw new SQLException("Connection routing failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during connection routing", e);
            throw new SQLException("Unexpected routing error: " + e.getMessage(), e);
        }
    }

    /**
     * Execute an operation with explicit tenant context.
     * The shard DataSource is determined by the TenantInfo set in the context.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws SQLException if the operation fails
     */
    public <T> T executeWithContext(SqlOperation<T> operation) throws SQLException {
        return operation.execute(this);
    }

    /**
     * Execute an operation for a tenant context with pre-resolved shard.
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
                return executeWithContext(operation);
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
    public ShardDataSourceRouter.RoutingStatistics getRoutingStatistics() {
        return shardAwareDataSourceDelegate.getRoutingStatistics();
    }

    /**
     * Check if a specific shard is available for routing.
     *
     * @param shardId the shard identifier
     * @return true if shard is available
     */
    public boolean isShardAvailable(String shardId) {
        return shardAwareDataSourceDelegate.isShardAvailable(shardId);
    }

    /**
     * Get the underlying connection router.
     *
     * @return the connection router
     */
    public ShardDataSourceRouter getConnectionRouter() {
        return shardAwareDataSourceDelegate;
    }



    /**
     * Functional interface for SQL operations.
     */
    @FunctionalInterface
    public interface SqlOperation<T> {
        T execute(DataSource dataSource) throws SQLException;
    }
}