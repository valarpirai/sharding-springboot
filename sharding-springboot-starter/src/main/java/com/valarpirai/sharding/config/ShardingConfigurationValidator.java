package com.valarpirai.sharding.config;

import com.valarpirai.sharding.validation.EntityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Validates sharding configuration and entities during application startup.
 * Runs after the application context is fully initialized.
 */
@Component
@Order(1000) // Run late in the startup process
public class ShardingConfigurationValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ShardingConfigurationValidator.class);

    private final ShardingConfigProperties shardingConfig;
    private final EntityValidator entityValidator;

    public ShardingConfigurationValidator(ShardingConfigProperties shardingConfig,
                                        EntityValidator entityValidator) {
        this.shardingConfig = shardingConfig;
        this.entityValidator = entityValidator;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("Starting Galaxy Sharding configuration validation");

        try {
            // Validate configuration properties
            validateConfiguration();

            // Validate entities
            validateEntities();

            logger.info("Galaxy Sharding configuration validation completed successfully");

        } catch (Exception e) {
            logger.error("Galaxy Sharding configuration validation failed", e);
            throw new RuntimeException("Sharding configuration validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate the sharding configuration properties.
     */
    private void validateConfiguration() {
        logger.debug("Validating sharding configuration properties");

        // Validate global database configuration
        validateGlobalDatabaseConfig();

        // Validate shard configurations
        validateShardConfigurations();

        // Validate tenant column configuration
        validateTenantColumnConfiguration();

        logger.debug("Configuration properties validation passed");
    }

    /**
     * Validate global database configuration.
     */
    private void validateGlobalDatabaseConfig() {
        ShardingConfigProperties.GlobalDatabaseConfig globalDb = shardingConfig.getGlobalDb();

        if (globalDb.getUrl() == null || globalDb.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Global database URL is required: app.sharding.global-db.url");
        }

        if (globalDb.getUsername() == null || globalDb.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Global database username is required: app.sharding.global-db.username");
        }

        if (globalDb.getPassword() == null) {
            logger.warn("Global database password is not set. This may cause connection failures.");
        }

        logger.debug("Global database configuration is valid");
    }

    /**
     * Validate shard configurations.
     */
    private void validateShardConfigurations() {
        if (shardingConfig.getShards().isEmpty()) {
            throw new IllegalArgumentException("At least one shard must be configured");
        }

        int latestShardCount = 0;
        for (Map.Entry<String, ShardConfigProperties> entry : shardingConfig.getShards().entrySet()) {
            String shardId = entry.getKey();
            ShardConfigProperties shardConfig = entry.getValue();

            validateShardConfiguration(shardId, shardConfig);

            if (Boolean.TRUE.equals(shardConfig.getLatest())) {
                latestShardCount++;
            }
        }

        if (latestShardCount == 0) {
            throw new IllegalArgumentException("Exactly one shard must be marked as 'latest' for new tenant signups");
        }

        if (latestShardCount > 1) {
            throw new IllegalArgumentException("Only one shard can be marked as 'latest'. Found " + latestShardCount + " latest shards");
        }

        logger.debug("Shard configurations are valid: {} shards configured", shardingConfig.getShards().size());
    }

    /**
     * Validate individual shard configuration.
     */
    private void validateShardConfiguration(String shardId, ShardConfigProperties shardConfig) {
        // Validate master configuration
        if (shardConfig.getMaster() == null) {
            throw new IllegalArgumentException("Master configuration is required for shard: " + shardId);
        }

        validateDatabaseConfig(shardId + ".master", shardConfig.getMaster());

        // Validate replica configurations
        for (Map.Entry<String, DatabaseConfigProperties> replicaEntry : shardConfig.getReplicas().entrySet()) {
            String replicaName = replicaEntry.getKey();
            DatabaseConfigProperties replicaConfig = replicaEntry.getValue();
            validateDatabaseConfig(shardId + "." + replicaName, replicaConfig);
        }
    }

    /**
     * Validate database configuration (master or replica).
     */
    private void validateDatabaseConfig(String configName, DatabaseConfigProperties dbConfig) {
        if (dbConfig.getUrl() == null || dbConfig.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Database URL is required for: " + configName);
        }

        if (dbConfig.getUsername() == null || dbConfig.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Database username is required for: " + configName);
        }

        if (dbConfig.getPassword() == null) {
            logger.warn("Database password is not set for: {}. This may cause connection failures.", configName);
        }
    }

    /**
     * Validate tenant column configuration.
     */
    private void validateTenantColumnConfiguration() {
        if (shardingConfig.getTenantColumnNames().isEmpty()) {
            throw new IllegalArgumentException("At least one tenant column name must be configured");
        }

        for (String columnName : shardingConfig.getTenantColumnNames()) {
            if (columnName == null || columnName.trim().isEmpty()) {
                throw new IllegalArgumentException("Tenant column names cannot be null or empty");
            }

            // Basic validation for SQL identifier format
            if (!isValidSqlIdentifier(columnName)) {
                throw new IllegalArgumentException("Invalid tenant column name: " + columnName +
                                                 ". Column names must be valid SQL identifiers");
            }
        }

        logger.debug("Tenant column configuration is valid: {}", shardingConfig.getTenantColumnNames());
    }

    /**
     * Validate entities using the EntityValidator.
     */
    private void validateEntities() {
        logger.debug("Validating sharded entity annotations");

        entityValidator.validateAllEntities();

        EntityValidator.EntityValidationSummary summary = entityValidator.getValidationSummary();
        logger.info("Entity validation completed: {}", summary);
    }

    /**
     * Check if a string is a valid SQL identifier.
     */
    private boolean isValidSqlIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }

        // Basic check: starts with letter or underscore, contains only letters, digits, underscores
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}