package com.valarpirai.sharding.config;

import com.valarpirai.sharding.aspect.RepositoryShardingAspect;
import com.valarpirai.sharding.cache.CacheStatisticsService;
import com.valarpirai.sharding.iterator.TenantIterator;
import com.valarpirai.sharding.lookup.DatabaseSqlProviderFactory;
import com.valarpirai.sharding.lookup.ShardLookupService;
import com.valarpirai.sharding.lookup.ShardUtils;
import com.valarpirai.sharding.routing.ConnectionRouter;
import com.valarpirai.sharding.routing.RoutingDataSource;
import com.valarpirai.sharding.routing.ShardDataSources;
import com.valarpirai.sharding.validation.EntityValidator;
import com.valarpirai.sharding.validation.QueryValidator;
import com.valarpirai.sharding.validation.ValidatingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-configuration class for the Galaxy Sharding library.
 * Sets up all necessary beans and validates configuration.
 */
@Configuration
@EnableConfigurationProperties(ShardingConfigProperties.class)
@Import(CacheConfiguration.class)
@EnableAspectJAutoProxy
public class ShardingAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ShardingAutoConfiguration.class);

    private final ShardingConfigProperties shardingConfig;

    public ShardingAutoConfiguration(ShardingConfigProperties shardingConfig) {
        this.shardingConfig = shardingConfig;
    }

    @PostConstruct
    public void logConfiguration() {
        logger.info("Initializing Galaxy Sharding with {} shards, {} tenant columns, validation: {}, caching: {} ({})",
                   shardingConfig.getShards().size(),
                   shardingConfig.getTenantColumnNames().size(),
                   shardingConfig.getValidation().getStrictness(),
                   shardingConfig.getCache().isEnabled() ? "enabled" : "disabled",
                   shardingConfig.getCache().getType());
    }

    /**
     * Global database DataSource for tenant_shard_mapping table.
     */
    @Bean("globalDataSource")
    @ConditionalOnMissingBean(name = "globalDataSource")
    public DataSource globalDataSource() {
        logger.info("Creating global database DataSource");

        ShardingConfigProperties.GlobalDatabaseConfig globalConfig = shardingConfig.getGlobalDb();

        HikariConfig config = HikariConfigUtil.createHikariConfig(
                globalConfig.getHikari(),
                createDatabaseConfig(globalConfig),
                "global-db-pool"
        );

        // Apply database-specific optimizations
        HikariConfigUtil.applyDatabaseSpecificOptimizations(config);
        HikariConfigUtil.validateConfiguration(config);

        return new HikariDataSource(config);
    }

    /**
     * JdbcTemplate for global database operations.
     */
    @Bean("globalJdbcTemplate")
    @ConditionalOnMissingBean(name = "globalJdbcTemplate")
    public JdbcTemplate globalJdbcTemplate(DataSource globalDataSource) {
        return new JdbcTemplate(globalDataSource);
    }

    /**
     * Database SQL provider factory for database-agnostic operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseSqlProviderFactory databaseSqlProviderFactory() {
        return new DatabaseSqlProviderFactory();
    }

    /**
     * Shard lookup service for tenant-shard mapping operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardLookupService shardLookupService(JdbcTemplate globalJdbcTemplate,
                                                DatabaseSqlProviderFactory sqlProviderFactory) {
        return new ShardLookupService(globalJdbcTemplate, shardingConfig, sqlProviderFactory);
    }

    /**
     * Shard utilities for convenient shard operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardUtils shardUtils(ShardLookupService shardLookupService) {
        return new ShardUtils(shardLookupService, shardingConfig);
    }

    /**
     * Tenant iterator for batch processing operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantIterator tenantIterator(ShardLookupService shardLookupService) {
        return new TenantIterator(shardLookupService);
    }

    /**
     * Query validator for SQL query validation.
     */
    @Bean
    @ConditionalOnMissingBean
    public QueryValidator queryValidator() {
        return new QueryValidator(shardingConfig);
    }

    /**
     * Entity validator for @ShardedEntity annotation validation.
     */
    @Bean
    @ConditionalOnMissingBean
    public EntityValidator entityValidator(ApplicationContext applicationContext) {
        return new EntityValidator(shardingConfig, applicationContext);
    }

    /**
     * Configuration validator that runs during application startup.
     */
    @Bean
    @ConditionalOnMissingBean
    public ShardingConfigurationValidator shardingConfigurationValidator(EntityValidator entityValidator) {
        return new ShardingConfigurationValidator(shardingConfig, entityValidator);
    }

    /**
     * Create all shard DataSources.
     */
    @Bean("shardDataSources")
    @ConditionalOnMissingBean(name = "shardDataSources")
    public Map<String, ShardDataSources> shardDataSources() {
        logger.info("Creating shard DataSources for {} shards", shardingConfig.getShards().size());

        Map<String, ShardDataSources> shardDataSources = new ConcurrentHashMap<>();

        for (Map.Entry<String, ShardConfigProperties> entry : shardingConfig.getShards().entrySet()) {
            String shardId = entry.getKey();
            ShardConfigProperties shardConfig = entry.getValue();

            logger.info("Creating DataSources for shard: {} (latest: {}, status: {})",
                       shardId, shardConfig.getLatest(), shardConfig.getStatus());

            ShardDataSources dataSources = createShardDataSources(shardId, shardConfig);
            shardDataSources.put(shardId, dataSources);
        }

        return shardDataSources;
    }

    /**
     * Connection router for routing connections to appropriate shards.
     */
    @Bean
    @ConditionalOnMissingBean
    public ConnectionRouter connectionRouter(Map<String, ShardDataSources> shardDataSources,
                                           DataSource globalDataSource,
                                           ShardLookupService shardLookupService) {
        return new ConnectionRouter(shardLookupService, shardDataSources, globalDataSource);
    }

    /**
     * Cache statistics service for monitoring cache performance.
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheStatisticsService cacheStatisticsService(CacheManager cacheManager) {
        return new CacheStatisticsService(cacheManager, shardingConfig);
    }

    /**
     * Repository sharding aspect for automatic sharded entity context management.
     * This aspect automatically sets the appropriate context for repository operations
     * based on the @ShardedEntity annotation of the entity class.
     */
    @Bean
    @ConditionalOnMissingBean
    public RepositoryShardingAspect repositoryShardingAspect() {
        logger.info("Creating RepositoryShardingAspect for automatic sharded entity context management");
        return new RepositoryShardingAspect();
    }

    /**
     * Primary DataSource with routing and validation capabilities.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public DataSource primaryDataSource(ConnectionRouter connectionRouter,
                                       QueryValidator queryValidator) {
        logger.info("Creating primary routing DataSource with query validation");

        RoutingDataSource routingDataSource = new RoutingDataSource(connectionRouter);
        return new ValidatingDataSource(routingDataSource, queryValidator);
    }

    /**
     * Create ShardDataSources for a specific shard.
     */
    private ShardDataSources createShardDataSources(String shardId, ShardConfigProperties shardConfig) {
        // Create master DataSource
        DataSource masterDataSource = createDataSource(
                shardId + "-master",
                shardConfig.getMaster(),
                shardConfig.getHikari()
        );

        // Create replica DataSources
        Map<String, DataSource> replicaDataSources = new HashMap<>();
        for (Map.Entry<String, DatabaseConfigProperties> replicaEntry : shardConfig.getReplicas().entrySet()) {
            String replicaName = replicaEntry.getKey();
            DatabaseConfigProperties replicaConfig = replicaEntry.getValue();

            String poolName = shardId + "-" + replicaName;
            DataSource replicaDataSource = createDataSource(poolName, replicaConfig, shardConfig.getHikari());
            replicaDataSources.put(replicaName, replicaDataSource);
        }

        // Create ShardDataSources with replicas
        ShardDataSources dataSources = new ShardDataSources(shardId, masterDataSource);
        replicaDataSources.values().forEach(dataSources::addReplica);

        logger.debug("Created shard DataSources for {}: 1 master, {} replicas",
                    shardId, replicaDataSources.size());

        return dataSources;
    }

    /**
     * Create a DataSource with HikariCP configuration.
     */
    private DataSource createDataSource(String poolName,
                                      DatabaseConfigProperties dbConfig,
                                      HikariConfigProperties hikariConfig) {
        logger.debug("Creating DataSource: {}", poolName);

        HikariConfig config = HikariConfigUtil.createHikariConfig(hikariConfig, dbConfig, poolName);

        // Apply database-specific optimizations
        HikariConfigUtil.applyDatabaseSpecificOptimizations(config);
        HikariConfigUtil.validateConfiguration(config);

        return new HikariDataSource(config);
    }

    /**
     * Create DatabaseConfigProperties from GlobalDatabaseConfig.
     */
    private DatabaseConfigProperties createDatabaseConfig(ShardingConfigProperties.GlobalDatabaseConfig globalConfig) {
        DatabaseConfigProperties dbConfig = new DatabaseConfigProperties();
        dbConfig.setUrl(globalConfig.getUrl());
        dbConfig.setUsername(globalConfig.getUsername());
        dbConfig.setPassword(globalConfig.getPassword());
        dbConfig.setDriverClassName(globalConfig.getDriverClassName());
        return dbConfig;
    }
}