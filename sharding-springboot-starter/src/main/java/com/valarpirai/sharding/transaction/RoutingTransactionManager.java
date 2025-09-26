package com.valarpirai.sharding.transaction;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import com.valarpirai.sharding.observability.OpenTelemetryConfiguration;
import com.valarpirai.sharding.routing.ConnectionRouter;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A routing transaction manager that delegates to the appropriate transaction manager
 * based on the current tenant context and whether the operation is for sharded entities.
 *
 * This manager creates and caches DataSourceTransactionManager instances for each
 * DataSource (global and shards) and routes transactions appropriately.
 *
 * Uses delegation pattern instead of inheritance to avoid protected method access issues.
 */
public class RoutingTransactionManager implements PlatformTransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(RoutingTransactionManager.class);

    private final ConnectionRouter connectionRouter;
    private final DataSource globalDataSource;

    // Cache of transaction managers for each DataSource
    private final Map<DataSource, PlatformTransactionManager> transactionManagers =
            new ConcurrentHashMap<>();

    // OpenTelemetry components - optional dependencies
    @Autowired(required = false)
    private Tracer tracer;

    @Autowired(required = false)
    private LongCounter transactionCounter;

    @Autowired(required = false)
    private OpenTelemetryConfiguration.ShardingObservabilityUtils observabilityUtils;

    public RoutingTransactionManager(ConnectionRouter connectionRouter, DataSource globalDataSource) {
        this.connectionRouter = connectionRouter;
        this.globalDataSource = globalDataSource;

        // Pre-create global transaction manager
        getOrCreateTransactionManager(globalDataSource);
    }

    @Override
    @WithSpan("sharding.transaction.get_transaction")
    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        TenantInfo tenantInfo = TenantContext.getTenantInfo();

        Span currentSpan = Span.current();
        if (tenantInfo != null && currentSpan != null) {
            currentSpan.setAllAttributes(createTransactionAttributes(tenantInfo, "get_transaction"));
        }

        try {
            PlatformTransactionManager targetTxManager = determineTargetTransactionManager();
            logger.debug("Routing transaction to: {}", targetTxManager.getClass().getSimpleName());

            TransactionStatus status = targetTxManager.getTransaction(definition);

            // Record metrics
            recordTransactionMetrics(tenantInfo, "begin", "success");

            return status;
        } catch (TransactionException e) {
            recordTransactionMetrics(tenantInfo, "begin", "error");
            currentSpan.recordException(e);
            throw e;
        }
    }

    @Override
    @WithSpan("sharding.transaction.commit")
    public void commit(TransactionStatus status) throws TransactionException {
        TenantInfo tenantInfo = TenantContext.getTenantInfo();

        Span currentSpan = Span.current();
        if (tenantInfo != null && currentSpan != null) {
            currentSpan.setAllAttributes(createTransactionAttributes(tenantInfo, "commit"));
        }

        try {
            // The transaction status should contain the reference to the correct transaction manager
            // Since we're delegating, the status is from the target transaction manager
            PlatformTransactionManager targetTxManager = determineTargetTransactionManager();
            targetTxManager.commit(status);

            // Record metrics
            recordTransactionMetrics(tenantInfo, "commit", "success");
        } catch (TransactionException e) {
            recordTransactionMetrics(tenantInfo, "commit", "error");
            currentSpan.recordException(e);
            throw e;
        }
    }

    @Override
    @WithSpan("sharding.transaction.rollback")
    public void rollback(TransactionStatus status) throws TransactionException {
        TenantInfo tenantInfo = TenantContext.getTenantInfo();

        Span currentSpan = Span.current();
        if (tenantInfo != null && currentSpan != null) {
            currentSpan.setAllAttributes(createTransactionAttributes(tenantInfo, "rollback"));
        }

        try {
            // The transaction status should contain the reference to the correct transaction manager
            // Since we're delegating, the status is from the target transaction manager
            PlatformTransactionManager targetTxManager = determineTargetTransactionManager();
            targetTxManager.rollback(status);

            // Record metrics
            recordTransactionMetrics(tenantInfo, "rollback", "success");
        } catch (TransactionException e) {
            recordTransactionMetrics(tenantInfo, "rollback", "error");
            currentSpan.recordException(e);
            throw e;
        }
    }

    /**
     * Determine which transaction manager to use based on current context.
     * Uses the same logic as RoutingDataSource to ensure consistency.
     */
    private PlatformTransactionManager determineTargetTransactionManager() {
        try {
            // Check if we have pre-resolved shard information
            TenantInfo tenantInfo = TenantContext.getTenantInfo();

            if (tenantInfo != null && tenantInfo.shardDataSource() != null) {
                // Use pre-resolved shard DataSource
                DataSource shardDataSource = tenantInfo.shardDataSource();
                logger.debug("Using pre-resolved shard transaction manager for tenant: {}, shard: {}",
                           tenantInfo.tenantId(), tenantInfo.shardId());
                return getOrCreateTransactionManager(shardDataSource);
            }

            // Fallback: try to determine shard dynamically
            // This will typically use the global DataSource unless tenant context suggests otherwise
            if (tenantInfo != null && tenantInfo.tenantId() != null) {
                try {
                    // Try to get shard DataSource from connection router
                    DataSource shardDataSource = connectionRouter.getShardDataSource(
                        tenantInfo.shardId(), tenantInfo.readOnlyMode());
                    logger.debug("Using dynamically resolved shard transaction manager for tenant: {}",
                               tenantInfo.tenantId());
                    return getOrCreateTransactionManager(shardDataSource);
                } catch (Exception e) {
                    logger.debug("Could not resolve shard DataSource dynamically, using global: {}",
                               e.getMessage());
                }
            }

            // Default to global database transaction manager
            logger.debug("Using global transaction manager");
            return getOrCreateTransactionManager(globalDataSource);

        } catch (Exception e) {
            logger.error("Error determining target transaction manager, falling back to global: {}",
                        e.getMessage(), e);
            return getOrCreateTransactionManager(globalDataSource);
        }
    }

    /**
     * Get or create a DataSourceTransactionManager for the given DataSource.
     * Transaction managers are cached to avoid creating multiple instances for the same DataSource.
     */
    private PlatformTransactionManager getOrCreateTransactionManager(DataSource dataSource) {
        return transactionManagers.computeIfAbsent(dataSource, ds -> {
            logger.debug("Creating new DataSourceTransactionManager for DataSource: {}",
                        ds.getClass().getSimpleName());

            DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);

            // Configure transaction manager with sensible defaults
            txManager.setDefaultTimeout(-1); // No timeout by default
            txManager.setNestedTransactionAllowed(true);
            txManager.setValidateExistingTransaction(false);
            txManager.setGlobalRollbackOnParticipationFailure(true);
            txManager.setFailEarlyOnGlobalRollbackOnly(false);
            txManager.setRollbackOnCommitFailure(false);

            return txManager;
        });
    }

    /**
     * Get statistics about cached transaction managers.
     */
    public int getCachedTransactionManagerCount() {
        return transactionManagers.size();
    }

    /**
     * Clear transaction manager cache (useful for testing or reconfiguration).
     */
    public void clearTransactionManagerCache() {
        transactionManagers.clear();
        logger.info("Cleared transaction manager cache");
    }

    /**
     * Create attributes for transaction operations.
     */
    private Attributes createTransactionAttributes(TenantInfo tenantInfo, String operationType) {
        if (observabilityUtils == null) {
            return Attributes.empty();
        }

        String shardId = tenantInfo != null ? tenantInfo.shardId() : null;
        Long tenantId = tenantInfo != null ? tenantInfo.tenantId() : null;

        return observabilityUtils.createTransactionAttributes(shardId, tenantId, operationType);
    }

    /**
     * Record transaction metrics.
     */
    private void recordTransactionMetrics(TenantInfo tenantInfo, String transactionType, String status) {
        if (transactionCounter != null) {
            Attributes attributes = createTransactionAttributes(tenantInfo, transactionType)
                .toBuilder()
                .put(OpenTelemetryConfiguration.OPERATION_TYPE, status)
                .build();
            transactionCounter.add(1, attributes);
        }
    }
}