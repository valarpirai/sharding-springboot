package com.valarpirai.sharding.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShardingConfigProperties.
 */
class ShardingConfigPropertiesTest {

    private ShardingConfigProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ShardingConfigProperties();
    }

    @Test
    void testDefaultValues() {
        // Test default values
        assertNotNull(properties.getGlobalDb());
        assertNotNull(properties.getShards());
        assertTrue(properties.getShards().isEmpty());
        assertNotNull(properties.getTenantColumnNames());
        assertTrue(properties.getTenantColumnNames().contains("tenant_id"));
        assertTrue(properties.getTenantColumnNames().contains("company_id"));
        assertNotNull(properties.getCache());
        assertTrue(properties.getCache().isEnabled());
        assertEquals(ShardingConfigProperties.CacheType.CAFFEINE, properties.getCache().getType());
        assertEquals(1, properties.getCache().getTtlHours());
    }

    @Test
    void testGlobalDatabaseConfig() {
        ShardingConfigProperties.GlobalDatabaseConfig globalDb = properties.getGlobalDb();

        globalDb.setUrl("jdbc:mysql://localhost:3306/global");
        globalDb.setUsername("test_user");
        globalDb.setPassword("test_password");
        globalDb.setDriverClassName("com.mysql.cj.jdbc.Driver");

        assertEquals("jdbc:mysql://localhost:3306/global", globalDb.getUrl());
        assertEquals("test_user", globalDb.getUsername());
        assertEquals("test_password", globalDb.getPassword());
        assertEquals("com.mysql.cj.jdbc.Driver", globalDb.getDriverClassName());
        assertNotNull(globalDb.getHikari());
    }

    @Test
    void testCacheConfig() {
        ShardingConfigProperties.CacheConfig cache = properties.getCache();

        // Test default values
        assertTrue(cache.isEnabled());
        assertEquals(ShardingConfigProperties.CacheType.CAFFEINE, cache.getType());
        assertEquals(1, cache.getTtlHours());
        assertEquals(10000, cache.getMaxSize());
        assertTrue(cache.isRecordStats());

        // Test Redis settings defaults
        assertEquals("sharding:tenant:", cache.getRedisKeyPrefix());
        assertEquals(6379, cache.getRedisPort());
        assertEquals("localhost", cache.getRedisHost());
        assertEquals(0, cache.getRedisDatabase());
        assertEquals(2000, cache.getRedisConnectionTimeoutMs());

        // Test setters
        cache.setEnabled(false);
        cache.setType(ShardingConfigProperties.CacheType.REDIS);
        cache.setTtlHours(2);
        cache.setMaxSize(5000);
        cache.setRecordStats(false);

        assertFalse(cache.isEnabled());
        assertEquals(ShardingConfigProperties.CacheType.REDIS, cache.getType());
        assertEquals(2, cache.getTtlHours());
        assertEquals(5000, cache.getMaxSize());
        assertFalse(cache.isRecordStats());

        // Test Redis-specific settings
        cache.setRedisHost("redis.example.com");
        cache.setRedisPort(6380);
        cache.setRedisDatabase(1);
        cache.setRedisPassword("secret");
        cache.setRedisKeyPrefix("myapp:tenant:");
        cache.setRedisConnectionTimeoutMs(5000);

        assertEquals("redis.example.com", cache.getRedisHost());
        assertEquals(6380, cache.getRedisPort());
        assertEquals(1, cache.getRedisDatabase());
        assertEquals("secret", cache.getRedisPassword());
        assertEquals("myapp:tenant:", cache.getRedisKeyPrefix());
        assertEquals(5000, cache.getRedisConnectionTimeoutMs());
    }

    @Test
    void testCacheTypeEnum() {
        // Test all enum values
        ShardingConfigProperties.CacheType[] types = ShardingConfigProperties.CacheType.values();
        assertEquals(2, types.length);

        // Test specific values
        assertNotNull(ShardingConfigProperties.CacheType.CAFFEINE);
        assertNotNull(ShardingConfigProperties.CacheType.REDIS);
    }

    @Test
    void testPropertyBinding() {
        // Test property binding using Spring Boot's Binder
        Map<String, Object> properties = Map.ofEntries(
            Map.entry("app.sharding.global-db.url", "jdbc:mysql://localhost:3306/test"),
            Map.entry("app.sharding.global-db.username", "testuser"),
            Map.entry("app.sharding.global-db.password", "testpass"),
            Map.entry("app.sharding.tenant-column-names[0]", "tenant_id"),
            Map.entry("app.sharding.tenant-column-names[1]", "org_id"),
            Map.entry("app.sharding.cache.enabled", "true"),
            Map.entry("app.sharding.cache.type", "REDIS"),
            Map.entry("app.sharding.cache.ttl-hours", "2"),
            Map.entry("app.sharding.cache.redis-host", "redis-server"),
            Map.entry("app.sharding.cache.redis-port", "6380"),
            Map.entry("app.sharding.shards.shard1.master.url", "jdbc:mysql://localhost:3306/shard1"),
            Map.entry("app.sharding.shards.shard1.master.username", "shard1user"),
            Map.entry("app.sharding.shards.shard1.latest", "true"),
            Map.entry("app.sharding.shards.shard1.region", "us-east-1")
        );

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

        Binder binder = Binder.get(environment);
        ShardingConfigProperties boundProperties = binder.bind("app.sharding", ShardingConfigProperties.class).get();

        // Verify bound properties
        assertEquals("jdbc:mysql://localhost:3306/test", boundProperties.getGlobalDb().getUrl());
        assertEquals("testuser", boundProperties.getGlobalDb().getUsername());
        assertEquals("testpass", boundProperties.getGlobalDb().getPassword());

        assertEquals(2, boundProperties.getTenantColumnNames().size());
        assertEquals("tenant_id", boundProperties.getTenantColumnNames().get(0));
        assertEquals("org_id", boundProperties.getTenantColumnNames().get(1));

        assertTrue(boundProperties.getCache().isEnabled());
        assertEquals(ShardingConfigProperties.CacheType.REDIS, boundProperties.getCache().getType());
        assertEquals(2, boundProperties.getCache().getTtlHours());
        assertEquals("redis-server", boundProperties.getCache().getRedisHost());
        assertEquals(6380, boundProperties.getCache().getRedisPort());

        assertEquals(1, boundProperties.getShards().size());
        ShardConfig shard1 = boundProperties.getShards().get("shard1");
        assertNotNull(shard1);
        assertEquals("jdbc:mysql://localhost:3306/shard1", shard1.getMaster().getUrl());
        assertEquals("shard1user", shard1.getMaster().getUsername());
        assertTrue(shard1.getLatest());
        assertEquals("us-east-1", shard1.getRegion());
    }

    @Test
    void testHikariConfigPropertiesDefaults() {
        HikariConfigProperties hikari = new HikariConfigProperties();

        // Test default values
        assertEquals(Integer.valueOf(20), hikari.getMaximumPoolSize());
        assertEquals(Integer.valueOf(5), hikari.getMinimumIdle());
        assertEquals(Duration.ofSeconds(30), hikari.getConnectionTimeout());
        assertEquals(Duration.ofMinutes(10), hikari.getIdleTimeout());
        assertEquals(Duration.ofMinutes(30), hikari.getMaxLifetime());
        assertEquals(Duration.ofSeconds(5), hikari.getValidationTimeout());
        assertEquals(Boolean.TRUE, hikari.getAutoCommit());
        assertEquals(Boolean.FALSE, hikari.getReadOnly());
        assertEquals(Boolean.TRUE, hikari.getRegisterMbeans());
        assertEquals(Integer.valueOf(1), hikari.getInitializationFailTimeout());
        assertEquals(Boolean.FALSE, hikari.getIsolateInternalQueries());
        assertEquals(Boolean.FALSE, hikari.getAllowPoolSuspension());
    }
}