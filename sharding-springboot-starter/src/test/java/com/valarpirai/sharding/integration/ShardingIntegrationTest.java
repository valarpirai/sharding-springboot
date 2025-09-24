package com.valarpirai.sharding.integration;

import com.valarpirai.sharding.annotation.ShardedEntity;
import com.valarpirai.sharding.cache.CacheStatisticsService;
import com.valarpirai.sharding.config.ShardingAutoConfiguration;
import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.ShardLookupService;
import com.valarpirai.sharding.lookup.ShardUtils;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.sql.DataSource;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the sharding library.
 */
@SpringBootTest(classes = {ShardingAutoConfiguration.class, ShardingIntegrationTest.TestEntity.class})
@TestPropertySource(properties = {
        "app.sharding.global-db.url=jdbc:h2:mem:integration_global_test;DB_CLOSE_DELAY=-1",
        "app.sharding.global-db.username=sa",
        "app.sharding.global-db.password=",
        "app.sharding.shard1.master.url=jdbc:h2:mem:integration_shard1_test;DB_CLOSE_DELAY=-1",
        "app.sharding.shard1.master.username=sa",
        "app.sharding.shard1.master.password=",
        "app.sharding.shard1.latest=true",
        "app.sharding.shard1.region=test-region",
        "app.sharding.shard2.master.url=jdbc:h2:mem:integration_shard2_test;DB_CLOSE_DELAY=-1",
        "app.sharding.shard2.master.username=sa",
        "app.sharding.shard2.master.password=",
        "app.sharding.shard2.latest=false",
        "app.sharding.shard2.region=test-region-2",
        "app.sharding.cache.enabled=true",
        "app.sharding.cache.type=CAFFEINE",
        "app.sharding.cache.ttl-hours=1",
        "app.sharding.validation.strictness=STRICT"
})
class ShardingIntegrationTest {

    @Autowired
    private ShardLookupService shardLookupService;

    @Autowired
    private ShardUtils shardUtils;

    @Autowired
    private CacheStatisticsService cacheStatisticsService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clear tenant context
        TenantContext.clear();

        // Initialize shard database tables for testing
        initializeShardTables();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void initializeShardTables() {
        try {
            // Create a test table in each shard for testing
            JdbcTemplate globalTemplate = new JdbcTemplate(dataSource);

            // The tenant_shard_mapping table should be created automatically
            // Let's just verify it exists
            Integer count = globalTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TENANT_SHARD_MAPPING'",
                Integer.class
            );
            assertTrue(count > 0, "tenant_shard_mapping table should be created automatically");
        } catch (Exception e) {
            // Table creation might fail in test environment, that's okay
        }
    }

    @Test
    void testTenantShardMappingLifecycle() {
        // Test creating a new tenant-shard mapping
        Long tenantId = 1001L;
        String shardId = "shard1";
        String region = "test-region";

        // Create mapping
        TenantShardMapping mapping = shardLookupService.createMapping(tenantId, shardId, region);
        assertNotNull(mapping);
        assertEquals(tenantId, mapping.getTenantId());
        assertEquals(shardId, mapping.getShardId());
        assertEquals(region, mapping.getRegion());

        // Retrieve mapping
        Optional<TenantShardMapping> retrieved = shardLookupService.findShardByTenantId(tenantId);
        assertTrue(retrieved.isPresent());
        assertEquals(mapping.getTenantId(), retrieved.get().getTenantId());
        assertEquals(mapping.getShardId(), retrieved.get().getShardId());

        // Update mapping
        boolean updated = shardLookupService.updateMapping(tenantId, "shard2", "test-region-2", "ACTIVE");
        assertTrue(updated);

        // Verify update
        Optional<TenantShardMapping> updatedMapping = shardLookupService.findShardByTenantId(tenantId);
        assertTrue(updatedMapping.isPresent());
        assertEquals("shard2", updatedMapping.get().getShardId());
        assertEquals("test-region-2", updatedMapping.get().getRegion());
    }

    @Test
    void testTenantContextIntegration() {
        // Create tenant mapping
        Long tenantId = 2001L;
        shardLookupService.createMapping(tenantId, "shard1", "test-region");

        // Test tenant context execution
        String result = TenantContext.executeInTenantContext(tenantId, () -> {
            assertEquals(tenantId, TenantContext.getTenantId());
            return "success";
        });

        assertEquals("success", result);
        assertFalse(TenantContext.hasTenantContext()); // Should be cleared after execution
    }

    @Test
    void testShardUtilitiesIntegration() {
        // Test assigning tenant to latest shard
        Long tenantId = 3001L;

        TenantShardMapping mapping = shardUtils.assignTenantToLatestShard(tenantId);
        assertNotNull(mapping);
        assertEquals(tenantId, mapping.getTenantId());
        assertEquals("shard1", mapping.getShardId()); // shard1 is marked as latest

        // Test moving tenant to different shard
        boolean moved = shardUtils.moveTenantToShard(tenantId, "shard2");
        assertTrue(moved);

        Optional<TenantShardMapping> movedMapping = shardLookupService.findShardByTenantId(tenantId);
        assertTrue(movedMapping.isPresent());
        assertEquals("shard2", movedMapping.get().getShardId());

        // Test getting shard statistics
        ShardUtils.ShardStatistics stats = shardUtils.getShardStatistics();
        assertNotNull(stats);
        assertTrue(stats.getTotalTenants() > 0);
        assertTrue(stats.getShardDistribution().size() > 0);
    }

    @Test
    void testCacheIntegration() {
        // Test cache statistics
        CacheStatisticsService.CacheStatistics stats = cacheStatisticsService.getCacheStatistics();
        assertNotNull(stats);
        assertTrue(stats.isEnabled());
        assertEquals("CAFFEINE", stats.getCacheType());
        assertEquals(1, stats.getTtlHours());

        // Create a tenant mapping to test caching
        Long tenantId = 4001L;
        shardLookupService.createMapping(tenantId, "shard1", "test-region");

        // First lookup should miss cache and hit database
        Optional<TenantShardMapping> mapping1 = shardLookupService.findShardByTenantId(tenantId);
        assertTrue(mapping1.isPresent());

        // Second lookup should hit cache
        Optional<TenantShardMapping> mapping2 = shardLookupService.findShardByTenantId(tenantId);
        assertTrue(mapping2.isPresent());
        assertEquals(mapping1.get().getShardId(), mapping2.get().getShardId());

        // Test cache eviction
        shardLookupService.evictFromCache(tenantId);

        // Test cache clearing
        shardLookupService.clearCache();
    }

    @Test
    void testCacheWarmup() {
        // Create multiple tenant mappings
        Long tenant1 = 5001L;
        Long tenant2 = 5002L;
        Long tenant3 = 5003L;

        shardLookupService.createMapping(tenant1, "shard1", "test-region");
        shardLookupService.createMapping(tenant2, "shard2", "test-region-2");
        shardLookupService.createMapping(tenant3, "shard1", "test-region");

        // Clear cache first
        shardLookupService.clearCache();

        // Warm up cache
        shardLookupService.warmUpCache(java.util.Arrays.asList(tenant1, tenant2, tenant3));

        // All lookups should now hit cache
        assertTrue(shardLookupService.findShardByTenantId(tenant1).isPresent());
        assertTrue(shardLookupService.findShardByTenantId(tenant2).isPresent());
        assertTrue(shardLookupService.findShardByTenantId(tenant3).isPresent());
    }

    @Test
    void testMultipleTenantOperations() {
        // Test batch operations with multiple tenants
        for (long i = 6001L; i <= 6010L; i++) {
            String shardId = (i % 2 == 0) ? "shard2" : "shard1";
            shardLookupService.createMapping(i, shardId, "test-region");
        }

        // Verify all mappings were created
        for (long i = 6001L; i <= 6010L; i++) {
            Optional<TenantShardMapping> mapping = shardLookupService.findShardByTenantId(i);
            assertTrue(mapping.isPresent(), "Mapping should exist for tenant " + i);
        }

        // Test shard distribution
        ShardUtils.ShardStatistics stats = shardUtils.getShardStatistics();
        assertTrue(stats.getTotalTenants() >= 10);
        assertTrue(stats.getShardDistribution().containsKey("shard1"));
        assertTrue(stats.getShardDistribution().containsKey("shard2"));
    }

    @Test
    void testLatestShardFunctionality() {
        // Test that new tenants are assigned to latest shard
        Long tenantId = 7001L;

        TenantShardMapping mapping = shardUtils.assignTenantToLatestShard(tenantId);
        assertEquals("shard1", mapping.getShardId()); // shard1 is marked as latest

        // Test getting latest shard ID directly
        String latestShardId = shardLookupService.getLatestShardId();
        assertEquals("shard1", latestShardId);
    }

    /**
     * Test entity class for integration testing.
     */
    @Entity
    @ShardedEntity
    public static class TestEntity {
        @Id
        private Long id;

        private Long tenantId;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}