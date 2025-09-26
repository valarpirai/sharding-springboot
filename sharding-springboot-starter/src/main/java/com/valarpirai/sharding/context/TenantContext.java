package com.valarpirai.sharding.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-local context for storing tenant information.
 * Provides global access to current tenant information across the application.
 */
public class TenantContext {

    private static final Logger logger = LoggerFactory.getLogger(TenantContext.class);

    private static final ThreadLocal<TenantInfo> tenantContext = new ThreadLocal<>();

    /**
     * Set the tenant information for the current thread.
     * @deprecated Use setTenantInfo(TenantInfo) with complete shard information instead.
     * This method creates incomplete TenantInfo without shard DataSource, causing performance issues.
     *
     * @param tenantId the tenant identifier
     */
    @Deprecated
    public static void setTenantId(Long tenantId) {
        throw new UnsupportedOperationException(
            "setTenantId(Long) is deprecated. Use setTenantInfo(TenantInfo) with pre-resolved shard DataSource. " +
            "TenantInfo should never be created without shard DataSource for performance reasons.");
    }

    /**
     * Set the complete tenant information for the current thread.
     *
     * @param tenantInfo the tenant information
     */
    public static void setTenantInfo(TenantInfo tenantInfo) {
        if (tenantInfo == null) {
            logger.warn("Attempting to set null tenant info");
            return;
        }

        TenantInfo existing = tenantContext.get();
        if (existing != null && !existing.equals(tenantInfo)) {
            logger.warn("Overriding existing tenant context. Existing: {}, New: {}",
                       existing, tenantInfo);
        }

        tenantContext.set(tenantInfo);
        logger.debug("Set tenant context: {}", tenantInfo);
    }

    /**
     * Get the current tenant ID.
     *
     * @return the tenant ID or null if not set
     */
    public static Long getCurrentTenantId() {
        TenantInfo info = tenantContext.get();
        return info != null ? info.tenantId() : null;
    }

    /**
     * Get the current shard ID.
     *
     * @return the shard ID or null if not set
     */
    public static String getCurrentShardId() {
        TenantInfo info = tenantContext.get();
        return info != null ? info.shardId() : null;
    }

    /**
     * Get the complete tenant information.
     *
     * @return the tenant information or null if not set
     */
    public static TenantInfo getTenantInfo() {
        return tenantContext.get();
    }

    /**
     * Check if read-only mode is enabled for the current tenant.
     *
     * @return true if read-only mode is enabled
     */
    public static boolean isReadOnlyMode() {
        TenantInfo info = tenantContext.get();
        return info != null && info.readOnlyMode();
    }

    /**
     * Enable or disable read-only mode for the current tenant.
     *
     * @param readOnlyMode true to enable read-only mode
     */
    public static void setReadOnlyMode(boolean readOnlyMode) {
        TenantInfo info = tenantContext.get();
        if (info != null) {
            TenantInfo updatedInfo = info.withReadOnlyMode(readOnlyMode);
            tenantContext.set(updatedInfo);
            logger.debug("Set read-only mode to {} for tenant {}", readOnlyMode, info.tenantId());
        } else {
            logger.warn("Cannot set read-only mode: no tenant context available");
        }
    }

    /**
     * Clear the tenant context for the current thread.
     */
    public static void clear() {
        TenantInfo info = tenantContext.get();
        if (info != null) {
            logger.debug("Clearing tenant context: {}", info);
        }
        tenantContext.remove();
    }

    /**
     * Execute a function within a specific tenant context.
     * @deprecated Use executeInTenantContext with complete TenantInfo instead.
     * This method cannot create proper TenantInfo with shard DataSource.
     *
     * @param tenantId the tenant ID to set
     * @param function the function to execute
     * @param <T> the return type
     * @return the result of the function
     */
    @Deprecated
    public static <T> T executeInTenantContext(Long tenantId, java.util.function.Supplier<T> function) {
        throw new UnsupportedOperationException(
            "executeInTenantContext(Long, Supplier) is deprecated. " +
            "Services should resolve complete TenantInfo with shard DataSource before context switching.");
    }

    /**
     * Execute a function within a specific tenant context.
     *
     * @param tenantInfo the complete tenant information
     * @param function the function to execute
     * @param <T> the return type
     * @return the result of the function
     */
    public static <T> T executeInTenantContext(TenantInfo tenantInfo, java.util.function.Supplier<T> function) {
        TenantInfo previousContext = getTenantInfo();
        try {
            setTenantInfo(tenantInfo);
            return function.get();
        } finally {
            if (previousContext != null) {
                setTenantInfo(previousContext);
            } else {
                clear();
            }
        }
    }

    /**
     * Execute a runnable within a specific tenant context.
     *
     * @param tenantInfo the complete tenant information
     * @param runnable the runnable to execute
     */
    public static void executeInTenantContext(TenantInfo tenantInfo, Runnable runnable) {
        executeInTenantContext(tenantInfo, () -> {
            runnable.run();
            return null;
        });
    }
}