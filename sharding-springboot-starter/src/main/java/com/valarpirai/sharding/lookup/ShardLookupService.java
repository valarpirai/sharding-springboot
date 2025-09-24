package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.config.ShardingConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for looking up tenant to shard mappings from the global database.
 * Provides caching and error handling for shard directory operations.
 */
@Service
public class ShardLookupService {

    private static final Logger logger = LoggerFactory.getLogger(ShardLookupService.class);

    private static final String SELECT_BY_TENANT_SQL =
            "SELECT tenant_id, shard_id, region, shard_status, created_at FROM tenant_shard_mapping WHERE tenant_id = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT tenant_id, shard_id, region, shard_status, created_at FROM tenant_shard_mapping";

    private static final String INSERT_MAPPING_SQL =
            "INSERT INTO tenant_shard_mapping (tenant_id, shard_id, region, shard_status, created_at) VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_SHARD_SQL =
            "UPDATE tenant_shard_mapping SET shard_id = ?, region = ?, shard_status = ? WHERE tenant_id = ?";

    private final JdbcTemplate globalJdbcTemplate;
    private final ShardingConfigProperties shardingConfig;
    private final DatabaseSqlProvider sqlProvider;
    private final TenantShardMappingRowMapper rowMapper = new TenantShardMappingRowMapper();

    public ShardLookupService(JdbcTemplate globalJdbcTemplate,
                             ShardingConfigProperties shardingConfig,
                             DatabaseSqlProviderFactory sqlProviderFactory) {
        this.globalJdbcTemplate = globalJdbcTemplate;
        this.shardingConfig = shardingConfig;
        this.sqlProvider = determineSqlProvider(globalJdbcTemplate.getDataSource(), sqlProviderFactory);
        ensureTableExists();
    }

    /**
     * Find the shard for a given tenant ID.
     * Results are cached for 1 hour by default to reduce database lookups.
     *
     * @param tenantId the tenant identifier
     * @return the tenant-shard mapping if found
     */
    @Cacheable(value = "tenantShardMappings", key = "#tenantId", unless = "#result.isEmpty()")
    public Optional<TenantShardMapping> findShardByTenantId(Long tenantId) {
        if (tenantId == null) {
            logger.warn("Attempted to lookup shard for null tenant ID");
            return Optional.empty();
        }

        try {
            logger.debug("Looking up shard for tenant: {}", tenantId);
            TenantShardMapping mapping = globalJdbcTemplate.queryForObject(
                    SELECT_BY_TENANT_SQL,
                    rowMapper,
                    tenantId
            );
            logger.debug("Found shard mapping: {}", mapping);
            return Optional.of(mapping);
        } catch (EmptyResultDataAccessException e) {
            logger.info("No shard mapping found for tenant: {}", tenantId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error looking up shard for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new ShardLookupException("Failed to lookup shard for tenant: " + tenantId, e);
        }
    }

    /**
     * Get all tenant-shard mappings.
     *
     * @return list of all mappings
     */
    public List<TenantShardMapping> findAllMappings() {
        try {
            logger.debug("Fetching all tenant-shard mappings");
            List<TenantShardMapping> mappings = globalJdbcTemplate.query(SELECT_ALL_SQL, rowMapper);
            logger.debug("Found {} tenant-shard mappings", mappings.size());
            return mappings;
        } catch (Exception e) {
            logger.error("Error fetching all tenant-shard mappings: {}", e.getMessage(), e);
            throw new ShardLookupException("Failed to fetch all tenant-shard mappings", e);
        }
    }

    /**
     * Create a new tenant-shard mapping.
     * Typically used when onboarding new tenants.
     *
     * @param tenantId the tenant ID
     * @param shardId the shard ID
     * @param region the region
     * @return the created mapping
     */
    public TenantShardMapping createMapping(Long tenantId, String shardId, String region) {
        return createMapping(tenantId, shardId, region, "ACTIVE");
    }

    /**
     * Create a new tenant-shard mapping with specified status.
     * The created mapping is cached to avoid immediate database lookups.
     *
     * @param tenantId the tenant ID
     * @param shardId the shard ID
     * @param region the region
     * @param status the shard status
     * @return the created mapping
     */
    @CachePut(value = "tenantShardMappings", key = "#tenantId")
    public TenantShardMapping createMapping(Long tenantId, String shardId, String region, String status) {
        if (tenantId == null || shardId == null) {
            throw new IllegalArgumentException("Tenant ID and shard ID cannot be null");
        }

        try {
            logger.info("Creating tenant-shard mapping: tenantId={}, shardId={}, region={}, status={}",
                       tenantId, shardId, region, status);

            LocalDateTime now = LocalDateTime.now();
            int updated = globalJdbcTemplate.update(
                    INSERT_MAPPING_SQL,
                    tenantId, shardId, region, status, Timestamp.valueOf(now)
            );

            if (updated == 1) {
                TenantShardMapping mapping = new TenantShardMapping(tenantId, shardId, region, status);
                mapping.setCreatedAt(now);
                logger.info("Successfully created tenant-shard mapping: {}", mapping);
                return mapping;
            } else {
                throw new ShardLookupException("Failed to create tenant-shard mapping");
            }
        } catch (Exception e) {
            logger.error("Error creating tenant-shard mapping for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new ShardLookupException("Failed to create tenant-shard mapping", e);
        }
    }

    /**
     * Update an existing tenant-shard mapping.
     * Cache entry is evicted to ensure fresh data on next lookup.
     *
     * @param tenantId the tenant ID
     * @param newShardId the new shard ID
     * @param newRegion the new region
     * @param newStatus the new status
     * @return true if updated successfully
     */
    @CacheEvict(value = "tenantShardMappings", key = "#tenantId")
    public boolean updateMapping(Long tenantId, String newShardId, String newRegion, String newStatus) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        try {
            logger.info("Updating tenant-shard mapping: tenantId={}, shardId={}, region={}, status={}",
                       tenantId, newShardId, newRegion, newStatus);

            int updated = globalJdbcTemplate.update(
                    UPDATE_SHARD_SQL,
                    newShardId, newRegion, newStatus, tenantId
            );

            boolean success = updated == 1;
            if (success) {
                logger.info("Successfully updated tenant-shard mapping for tenant: {}", tenantId);
            } else {
                logger.warn("No rows updated for tenant: {}", tenantId);
            }
            return success;
        } catch (Exception e) {
            logger.error("Error updating tenant-shard mapping for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new ShardLookupException("Failed to update tenant-shard mapping", e);
        }
    }

    /**
     * Get the latest shard configured for new tenant signups.
     *
     * @return the latest shard ID
     */
    public String getLatestShardId() {
        return shardingConfig.getShards().entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue().getLatest()))
                .map(entry -> entry.getKey())
                .findFirst()
                .orElseThrow(() -> new ShardLookupException("No shard is marked as latest"));
    }

    /**
     * Determine the appropriate SQL provider based on the global database connection.
     */
    private DatabaseSqlProvider determineSqlProvider(DataSource dataSource, DatabaseSqlProviderFactory factory) {
        try {
            String jdbcUrl = dataSource.getConnection().getMetaData().getURL();
            DatabaseSqlProvider provider = factory.getProvider(jdbcUrl);
            logger.info("Using {} SQL provider for global database", provider.getDatabaseType());
            return provider;
        } catch (Exception e) {
            logger.error("Failed to determine database type, falling back to MySQL provider", e);
            return new MySQLSqlProvider();
        }
    }

    /**
     * Ensure the tenant_shard_mapping table exists, create it if not.
     */
    private void ensureTableExists() {
        try {
            String checkTableSql = sqlProvider.getTableExistsQuery("tenant_shard_mapping");
            Integer count = globalJdbcTemplate.queryForObject(checkTableSql, Integer.class);

            if (count == null || count == 0) {
                logger.warn("tenant_shard_mapping table does not exist. Creating table...");

                // Create table
                String createTableSql = sqlProvider.getCreateTenantShardMappingTableSql();
                globalJdbcTemplate.execute(createTableSql);

                // Create indexes
                String[] indexSqls = sqlProvider.getCreateIndexesSql();
                for (String indexSql : indexSqls) {
                    try {
                        globalJdbcTemplate.execute(indexSql);
                    } catch (Exception indexException) {
                        logger.warn("Failed to create index: {}", indexSql, indexException);
                    }
                }

                logger.info("Successfully created tenant_shard_mapping table with indexes for {}",
                           sqlProvider.getDatabaseType());
            } else {
                logger.debug("tenant_shard_mapping table exists");
            }
        } catch (Exception e) {
            String createTableSql = sqlProvider.getCreateTenantShardMappingTableSql();
            String errorMessage = "Failed to create tenant_shard_mapping table. Please create it manually with:\n" + createTableSql;
            logger.error(errorMessage, e);
            throw new ShardLookupException(errorMessage, e);
        }
    }

    /**
     * Evict tenant-shard mapping from cache.
     * Useful for cache management and troubleshooting.
     *
     * @param tenantId the tenant ID to evict from cache
     */
    @CacheEvict(value = "tenantShardMappings", key = "#tenantId")
    public void evictFromCache(Long tenantId) {
        logger.debug("Evicted tenant {} from cache", tenantId);
    }

    /**
     * Clear all cached tenant-shard mappings.
     * Use with caution as this will cause temporary performance impact.
     */
    @CacheEvict(value = "tenantShardMappings", allEntries = true)
    public void clearCache() {
        logger.info("Cleared all tenant-shard mappings from cache");
    }

    /**
     * Warm up cache by loading frequently used tenant mappings.
     *
     * @param tenantIds list of tenant IDs to pre-load
     */
    public void warmUpCache(List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return;
        }

        logger.info("Warming up cache for {} tenants", tenantIds.size());
        int loaded = 0;

        for (Long tenantId : tenantIds) {
            try {
                findShardByTenantId(tenantId);
                loaded++;
            } catch (Exception e) {
                logger.debug("Failed to load tenant {} during cache warm-up: {}", tenantId, e.getMessage());
            }
        }

        logger.info("Cache warm-up completed: loaded {}/{} tenant mappings", loaded, tenantIds.size());
    }

    /**
     * Row mapper for TenantShardMapping results.
     */
    private static class TenantShardMappingRowMapper implements RowMapper<TenantShardMapping> {
        @Override
        public TenantShardMapping mapRow(ResultSet rs, int rowNum) throws SQLException {
            TenantShardMapping mapping = new TenantShardMapping();
            mapping.setTenantId(rs.getLong("tenant_id"));
            mapping.setShardId(rs.getString("shard_id"));
            mapping.setRegion(rs.getString("region"));
            mapping.setShardStatus(rs.getString("shard_status"));

            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                mapping.setCreatedAt(createdAtTs.toLocalDateTime());
            }

            return mapping;
        }
    }
}