package com.valarpirai.sharding.transaction;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import com.valarpirai.sharding.routing.ConnectionRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A routing transaction manager that delegates to the appropriate transaction manager
 * based on the current tenant context and whether the operation is for sharded entities.
 *
 * This manager creates and caches DataSourceTransactionManager instances for each
 * DataSource (global and shards) and routes transactions appropriately.
 */
public class RoutingTransactionManager extends AbstractPlatformTransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(RoutingTransactionManager.class);

    private final ConnectionRouter connectionRouter;
    private final DataSource globalDataSource;

    // Cache of transaction managers for each DataSource
    private final Map<DataSource, PlatformTransactionManager> transactionManagers =
            new ConcurrentHashMap<>();

    // ThreadLocal to track which transaction manager is being used for current transaction
    private final ThreadLocal<PlatformTransactionManager> currentTransactionManager =
            new ThreadLocal<>();

    public RoutingTransactionManager(ConnectionRouter connectionRouter, DataSource globalDataSource) {
        this.connectionRouter = connectionRouter;
        this.globalDataSource = globalDataSource;

        // Pre-create global transaction manager
        getOrCreateTransactionManager(globalDataSource);
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        PlatformTransactionManager targetTxManager = determineTargetTransactionManager();
        currentTransactionManager.set(targetTxManager);

        if (targetTxManager instanceof AbstractPlatformTransactionManager) {
            return ((AbstractPlatformTransactionManager) targetTxManager).doGetTransaction();
        }

        // For non-AbstractPlatformTransactionManager implementations
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        PlatformTransactionManager targetTxManager = currentTransactionManager.get();

        if (targetTxManager instanceof AbstractPlatformTransactionManager) {
            ((AbstractPlatformTransactionManager) targetTxManager).doBegin(transaction, definition);
        } else {
            // This shouldn't happen with DataSourceTransactionManager, but handle gracefully
            logger.warn("Cannot begin transaction on non-AbstractPlatformTransactionManager: {}",
                       targetTxManager.getClass().getSimpleName());
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        PlatformTransactionManager targetTxManager = currentTransactionManager.get();

        if (targetTxManager instanceof AbstractPlatformTransactionManager) {
            ((AbstractPlatformTransactionManager) targetTxManager).doCommit(status);
        } else {
            targetTxManager.commit(status);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        PlatformTransactionManager targetTxManager = currentTransactionManager.get();

        if (targetTxManager instanceof AbstractPlatformTransactionManager) {
            ((AbstractPlatformTransactionManager) targetTxManager).doRollback(status);
        } else {
            targetTxManager.rollback(status);
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        PlatformTransactionManager targetTxManager = currentTransactionManager.get();

        if (targetTxManager instanceof AbstractPlatformTransactionManager) {
            ((AbstractPlatformTransactionManager) targetTxManager).doCleanupAfterCompletion(transaction);
        }

        // Clear the current transaction manager reference
        currentTransactionManager.remove();
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        PlatformTransactionManager targetTxManager = currentTransactionManager.get();

        if (targetTxManager instanceof AbstractPlatformTransactionManager) {
            return ((AbstractPlatformTransactionManager) targetTxManager).isExistingTransaction(transaction);
        }

        return false;
    }

    /**
     * Determine which transaction manager to use based on current context.
     * Uses the same logic as RoutingDataSource to ensure consistency.
     */
    private PlatformTransactionManager determineTargetTransactionManager() {
        try {
            // Check if we have pre-resolved shard information
            TenantInfo tenantInfo = TenantContext.getTenantInfo();

            if (tenantInfo != null && tenantInfo.getShardDataSource() != null) {
                // Use pre-resolved shard DataSource
                DataSource shardDataSource = tenantInfo.getShardDataSource();
                logger.debug("Using pre-resolved shard transaction manager for tenant: {}, shard: {}",
                           tenantInfo.getTenantId(), tenantInfo.getShardId());
                return getOrCreateTransactionManager(shardDataSource);
            }

            // Fallback: try to determine shard dynamically
            // This will typically use the global DataSource unless tenant context suggests otherwise
            if (tenantInfo != null && tenantInfo.getTenantId() != null) {
                try {
                    // Try to get shard DataSource from connection router
                    DataSource shardDataSource = connectionRouter.getShardDataSource(
                        tenantInfo.getShardId(), tenantInfo.isReadOnlyMode());
                    logger.debug("Using dynamically resolved shard transaction manager for tenant: {}",
                               tenantInfo.getTenantId());
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

            // Copy transaction manager configuration from this manager
            txManager.setDefaultTimeout(getDefaultTimeout());
            txManager.setNestedTransactionAllowed(isNestedTransactionAllowed());
            txManager.setValidateExistingTransaction(isValidateExistingTransaction());
            txManager.setGlobalRollbackOnParticipationFailure(isGlobalRollbackOnParticipationFailure());
            txManager.setFailEarlyOnGlobalRollbackOnly(isFailEarlyOnGlobalRollbackOnly());
            txManager.setRollbackOnCommitFailure(isRollbackOnCommitFailure());

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
}