package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Routing DataSource that routes to the appropriate shard based on TenantContext.
 * This DataSource is used specifically for sharded entities.
 */
public class TenantContextDataSource extends AbstractDataSource {

    private static final Logger logger = LoggerFactory.getLogger(TenantContextDataSource.class);
    private final DataSource globalDataSource;

    public TenantContextDataSource(DataSource globalDataSource) {
        this.globalDataSource = globalDataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataSource targetDataSource = determineTargetDataSource();
        logger.debug("Routing sharded query to DataSource: {}", targetDataSource.getClass().getSimpleName());
        return targetDataSource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        DataSource targetDataSource = determineTargetDataSource();
        logger.debug("Routing sharded query with credentials to DataSource: {}", targetDataSource.getClass().getSimpleName());
        return targetDataSource.getConnection(username, password);
    }

    /**
     * Determines the target DataSource based on current TenantContext.
     * For sharded entities, this MUST use the shard DataSource from TenantContext.
     *
     * @return the appropriate DataSource for the current tenant
     * @throws SQLException if no valid shard DataSource is available
     */
    private DataSource determineTargetDataSource() throws SQLException {
        TenantInfo tenantInfo = TenantContext.getTenantInfo();

        logger.debug("=== SHARDED ROUTING DATASOURCE === Determining target DataSource");

        if (tenantInfo == null) {
            logger.warn("No TenantContext available for sharded entity operation - using global fallback");
            return globalDataSource;
        }

        if (tenantInfo.shardDataSource() == null) {
            logger.error("TenantContext exists but no shard DataSource available - tenant: {}, shard: {}",
                        tenantInfo.tenantId(), tenantInfo.shardId());
            throw new SQLException("No shard DataSource available for tenant: " + tenantInfo.tenantId());
        }

        logger.info("SHARDED ROUTING SUCCESS: Using shard DataSource for tenant: {}, shard: {}",
                   tenantInfo.tenantId(), tenantInfo.shardId());

        return tenantInfo.shardDataSource();
    }
}