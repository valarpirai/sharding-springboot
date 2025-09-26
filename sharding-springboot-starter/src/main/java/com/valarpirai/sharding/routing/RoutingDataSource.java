package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.annotation.ShardedEntity;
import com.valarpirai.sharding.aspect.RepositoryShardingAspect;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import com.valarpirai.sharding.observability.OpenTelemetryConfiguration;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    // RepositoryShardingAspect - optional dependency for entity-based routing
    @Autowired(required = false)
    private RepositoryShardingAspect repositoryShardingAspect;

    // OpenTelemetry components - optional dependencies
    @Autowired(required = false)
    private Tracer tracer;

    @Autowired(required = false)
    private LongCounter connectionCounter;

    @Autowired(required = false)
    private LongHistogram connectionAcquisitionLatency;

    @Autowired(required = false)
    private OpenTelemetryConfiguration.ShardingObservabilityUtils observabilityUtils;

    public RoutingDataSource(ConnectionRouter connectionRouter) {
        this.connectionRouter = connectionRouter;
    }

    @Override
    @WithSpan("sharding.datasource.get_connection")
    public Connection getConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        TenantInfo tenantInfo = TenantContext.getTenantInfo();

        Span currentSpan = Span.current();
        if (tenantInfo != null && currentSpan != null) {
            currentSpan.setAllAttributes(createConnectionAttributes(tenantInfo, "get_connection"));
        }

        try {
            DataSource targetDataSource = determineTargetDataSource();
            Connection connection = targetDataSource.getConnection();

            // Record metrics
            recordConnectionMetrics(tenantInfo, startTime, "success");

            logger.debug("Obtained connection from target DataSource for tenant: {}",
                        TenantContext.getCurrentTenantId());

            return connection;
        } catch (SQLException e) {
            recordConnectionMetrics(tenantInfo, startTime, "error");
            currentSpan.recordException(e);
            throw e;
        }
    }

    @Override
    @WithSpan("sharding.datasource.get_connection_with_credentials")
    public Connection getConnection(String username, String password) throws SQLException {
        long startTime = System.currentTimeMillis();
        TenantInfo tenantInfo = TenantContext.getTenantInfo();

        Span currentSpan = Span.current();
        if (tenantInfo != null && currentSpan != null) {
            currentSpan.setAllAttributes(createConnectionAttributes(tenantInfo, "get_connection_with_credentials"));
        }

        try {
            DataSource targetDataSource = determineTargetDataSource();
            Connection connection = targetDataSource.getConnection(username, password);

            // Record metrics
            recordConnectionMetrics(tenantInfo, startTime, "success");

            logger.debug("Obtained connection with credentials from target DataSource for tenant: {}",
                        TenantContext.getCurrentTenantId());

            return connection;
        } catch (SQLException e) {
            recordConnectionMetrics(tenantInfo, startTime, "error");
            currentSpan.recordException(e);
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
            // Check if RepositoryShardingAspect is forcing global DataSource
            boolean forceGlobal = repositoryShardingAspect != null &&
                                repositoryShardingAspect.shouldUseGlobalDataSource();

            if (forceGlobal) {
                logger.debug("Using global DataSource (forced by RepositoryShardingAspect)");
                return connectionRouter.routeDataSource(false);
            }

            // Check if we have pre-resolved shard information in TenantContext
            TenantInfo tenantInfo = TenantContext.getTenantInfo();

            if (logger.isDebugEnabled()) {
                Long tenantId = TenantContext.getCurrentTenantId();
                boolean readOnly = TenantContext.isReadOnlyMode();
                String shardId = tenantInfo != null ? tenantInfo.shardId() : "none";
                boolean hasPreResolvedShard = tenantInfo != null && tenantInfo.shardDataSource() != null;
                logger.debug("Routing connection - tenant: {}, readOnly: {}, shard: {}, preResolved: {}, forceGlobal: {}",
                           tenantId, readOnly, shardId, hasPreResolvedShard, forceGlobal);
            }

            // Use pre-resolved shard information if available
            if (tenantInfo != null && tenantInfo.shardDataSource() != null) {
                logger.debug("Using pre-resolved shard DataSource from TenantInfo");
                return tenantInfo.shardDataSource();
            }

            // If no tenant context or no pre-resolved shard, use global database
            if (tenantInfo == null) {
                logger.debug("No tenant context - using global DataSource");
                return connectionRouter.routeDataSource(false);
            }

            // Fallback to dynamic routing via ConnectionRouter (should rarely happen)
            logger.debug("Using ConnectionRouter for dynamic DataSource resolution");
            return connectionRouter.routeDataSource(true);

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
     * Create attributes for connection operations.
     */
    private Attributes createConnectionAttributes(TenantInfo tenantInfo, String operationType) {
        if (observabilityUtils == null) {
            return Attributes.empty();
        }

        String shardId = tenantInfo != null ? tenantInfo.shardId() : null;
        boolean hasShardDataSource = tenantInfo != null && tenantInfo.shardDataSource() != null;
        String dataSourceType = hasShardDataSource ? "shard" : "global";

        return observabilityUtils.createDataSourceAttributes(shardId, dataSourceType, operationType);
    }

    /**
     * Record connection metrics.
     */
    private void recordConnectionMetrics(TenantInfo tenantInfo, long startTime, String status) {
        if (connectionCounter != null) {
            Attributes attributes = createConnectionAttributes(tenantInfo, "connection")
                .toBuilder()
                .put(OpenTelemetryConfiguration.OPERATION_TYPE, status)
                .build();
            connectionCounter.add(1, attributes);
        }

        if (connectionAcquisitionLatency != null) {
            long duration = System.currentTimeMillis() - startTime;
            Attributes attributes = createConnectionAttributes(tenantInfo, "acquisition_latency")
                .toBuilder()
                .put(OpenTelemetryConfiguration.OPERATION_TYPE, status)
                .build();
            connectionAcquisitionLatency.record(duration, attributes);
        }
    }

    /**
     * Functional interface for SQL operations.
     */
    @FunctionalInterface
    public interface SqlOperation<T> {
        T execute(DataSource dataSource) throws SQLException;
    }
}