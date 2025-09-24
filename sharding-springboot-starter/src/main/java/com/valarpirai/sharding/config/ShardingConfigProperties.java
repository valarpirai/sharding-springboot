package com.valarpirai.sharding.config;

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

    public GlobalDatabaseConfig getGlobalDb() {
        return globalDb;
    }

    public void setGlobalDb(GlobalDatabaseConfig globalDb) {
        this.globalDb = globalDb;
    }

    public Map<String, ShardConfigProperties> getShards() {
        return shards;
    }

    public void setShards(Map<String, ShardConfigProperties> shards) {
        this.shards = shards;
    }

    public List<String> getTenantColumnNames() {
        return tenantColumnNames;
    }

    public void setTenantColumnNames(List<String> tenantColumnNames) {
        this.tenantColumnNames = tenantColumnNames;
    }

    public ValidationConfig getValidation() {
        return validation;
    }

    public void setValidation(ValidationConfig validation) {
        this.validation = validation;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    /**
     * Configuration for the global database containing tenant_shard_mapping table.
     */
    public static class GlobalDatabaseConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        @NestedConfigurationProperty
        private HikariConfigProperties hikari = new HikariConfigProperties();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public HikariConfigProperties getHikari() {
            return hikari;
        }

        public void setHikari(HikariConfigProperties hikari) {
            this.hikari = hikari;
        }
    }

    /**
     * Configuration for query validation settings.
     */
    public static class ValidationConfig {
        private StrictnessLevel strictness = StrictnessLevel.STRICT;

        public StrictnessLevel getStrictness() {
            return strictness;
        }

        public void setStrictness(StrictnessLevel strictness) {
            this.strictness = strictness;
        }
    }

    /**
     * Configuration for caching tenant-shard mappings.
     */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public CacheType getType() {
            return type;
        }

        public void setType(CacheType type) {
            this.type = type;
        }

        public int getTtlHours() {
            return ttlHours;
        }

        public void setTtlHours(int ttlHours) {
            this.ttlHours = ttlHours;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public boolean isRecordStats() {
            return recordStats;
        }

        public void setRecordStats(boolean recordStats) {
            this.recordStats = recordStats;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public int getRedisPort() {
            return redisPort;
        }

        public void setRedisPort(int redisPort) {
            this.redisPort = redisPort;
        }

        public String getRedisHost() {
            return redisHost;
        }

        public void setRedisHost(String redisHost) {
            this.redisHost = redisHost;
        }

        public int getRedisDatabase() {
            return redisDatabase;
        }

        public void setRedisDatabase(int redisDatabase) {
            this.redisDatabase = redisDatabase;
        }

        public String getRedisPassword() {
            return redisPassword;
        }

        public void setRedisPassword(String redisPassword) {
            this.redisPassword = redisPassword;
        }

        public int getRedisConnectionTimeoutMs() {
            return redisConnectionTimeoutMs;
        }

        public void setRedisConnectionTimeoutMs(int redisConnectionTimeoutMs) {
            this.redisConnectionTimeoutMs = redisConnectionTimeoutMs;
        }
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