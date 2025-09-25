package com.valarpirai.sharding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main configuration properties for the sharding module.
 * Supports flat configuration structure for shards and comprehensive HikariCP settings.
 */
@Data
@ConfigurationProperties(prefix = "app.sharding")
public class ShardingConfigProperties {

    @NestedConfigurationProperty
    private GlobalDatabaseConfig globalDb = new GlobalDatabaseConfig();

    private Map<String, ShardConfigProperties> shards = new HashMap<>();

    private List<String> tenantColumnNames = List.of("tenant_id", "company_id");

    @NestedConfigurationProperty
    private ValidationConfig validation = new ValidationConfig();

    @NestedConfigurationProperty
    private CacheConfig cache = new CacheConfig();


    /**
     * Configuration for the global database containing tenant_shard_mapping table.
     */
    @Data
    public static class GlobalDatabaseConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        @NestedConfigurationProperty
        private HikariConfigProperties hikari = new HikariConfigProperties();

    }

    /**
     * Configuration for query validation settings.
     */
    @Data
    public static class ValidationConfig {
        private StrictnessLevel strictness = StrictnessLevel.STRICT;

    }

    /**
     * Configuration for caching tenant-shard mappings.
     */
    @Data
    public static class CacheConfig {
        private boolean enabled = true;
        private CacheType type = CacheType.CAFFEINE;
        private int ttlHours = 1;
        private int maxSize = 10000;
        private boolean recordStats = true;

        // Redis-specific settings
        private String redisKeyPrefix = "sharding:tenant:";
        private int redisPort = 6379;
        private String redisHost = "localhost";
        private int redisDatabase = 0;
        private String redisPassword;
        private int redisConnectionTimeoutMs = 2000;

    }

    /**
     * Validation strictness levels.
     */
    public enum StrictnessLevel {
        STRICT,   // Throw exception if tenant_id missing
        WARN,     // Log warning but allow query
        LOG,      // Log info message
        DISABLED  // No validation
    }

    /**
     * Cache implementation types.
     */
    public enum CacheType {
        CAFFEINE,  // In-memory cache using Caffeine
        REDIS      // Distributed cache using Redis
    }
}