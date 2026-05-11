package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.TenantShardMappingRepository;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConnectionRouter.
 */
@ExtendWith(MockitoExtension.class)
class ShardDataSourceRouterTest {

    @Mock
    private com.valarpirai.sharding.lookup.ITenantShardMappingReadRepo shardLookupService;

    @Mock
    private DataSource globalDataSource;

    @Mock
    private DataSource shard1MasterDataSource;

    @Mock
    private DataSource shard1ReplicaDataSource;

    @Mock
    private DataSource shard2MasterDataSource;

    private Map<String, ShardDataSources> shardDataSources;
    private ShardDataSourceRouter shardAwareDataSourceDelegate;

    @BeforeEach
    void setUp() {
        // Clear tenant context
        TenantContext.clear();

        // Set up shard data sources
        shardDataSources = new HashMap<>();

        ShardDataSources shard1DataSources = new ShardDataSources("shard1", shard1MasterDataSource);
        shard1DataSources.addReplica(shard1ReplicaDataSource);
        shardDataSources.put("shard1", shard1DataSources);

        ShardDataSources shard2DataSources = new ShardDataSources("shard2", shard2MasterDataSource);
        shardDataSources.put("shard2", shard2DataSources);

        shardAwareDataSourceDelegate = new ShardDataSourceRouter(shardLookupService, shardDataSources, globalDataSource);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testRouteDataSourceForNonShardedEntity() {
        // When routing for non-sharded entity, should return global data source
        DataSource result = shardAwareDataSourceDelegate.routeDataSource(false);
        assertEquals(globalDataSource, result);
    }

    @Test
    void testRouteToShardedDataSourceWithValidMapping() {
        // Given
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "shard1", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // When
        DataSource result = shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId);

        // Then
        assertEquals(shard1MasterDataSource, result);
        verify(shardLookupService).findShardByTenantId(tenantId);
    }

    @Test
    void testGetShardDataSourceWithReadOnlyMode() {
        // Given
        String shardId = "shard1";
        boolean readOnly = true;

        // When
        DataSource result = shardAwareDataSourceDelegate.getShardDataSource(shardId, readOnly);

        // Then
        // Should return replica data source when in read-only mode
        assertEquals(shard1ReplicaDataSource, result);
    }

    @Test
    void testRouteToShardedDataSourceWithNoMapping() {
        // Given
        Long tenantId = 1001L;
        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(Exception.class, () -> {
            shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId);
        });

        verify(shardLookupService).findShardByTenantId(tenantId);
    }

    @Test
    void testRouteToShardedDataSourceWithInvalidShardId() {
        // Given
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "invalid_shard", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // When/Then
        assertThrows(Exception.class, () -> {
            shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId);
        });
    }

    @Test
    void testGetShardDataSourceWithReadOnlyModeButNoReplicas() {
        // Given
        String shardId = "shard2";
        boolean readOnly = true;

        // When
        DataSource result = shardAwareDataSourceDelegate.getShardDataSource(shardId, readOnly);

        // Then
        // Should fall back to master when no replicas available
        assertEquals(shard2MasterDataSource, result);
    }

    @Test
    void testIsShardAvailable() {
        // Test with existing shard
        assertTrue(shardAwareDataSourceDelegate.isShardAvailable("shard1"));
        assertTrue(shardAwareDataSourceDelegate.isShardAvailable("shard2"));

        // Test with non-existing shard
        assertFalse(shardAwareDataSourceDelegate.isShardAvailable("invalid_shard"));
    }

    @Test
    void testGetRoutingStatistics() {
        // Test routing statistics functionality
        ShardDataSourceRouter.RoutingStatistics stats = shardAwareDataSourceDelegate.getRoutingStatistics();

        assertNotNull(stats);
        assertEquals(2, stats.totalShards());
        assertEquals(1, stats.shardsWithReplicas());
        assertEquals(1, stats.getShardsWithoutReplicas());
    }

    @Test
    void testRoutingStatisticsAfterRequests() {
        // Given
        Long tenantId1 = 1001L;
        Long tenantId2 = 2001L;

        TenantShardMapping mapping1 = new TenantShardMapping(tenantId1, "shard1", "us-east-1", "ACTIVE");
        mapping1.setCreatedAt(java.time.LocalDateTime.now());

        TenantShardMapping mapping2 = new TenantShardMapping(tenantId2, "shard2", "us-west-2", "ACTIVE");
        mapping2.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId1)).thenReturn(Optional.of(mapping1));
        when(shardLookupService.findShardByTenantId(tenantId2)).thenReturn(Optional.of(mapping2));

        // When - make some routing requests using direct method calls
        shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId1);
        shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId2);

        // Global request
        shardAwareDataSourceDelegate.routeDataSource(false);

        // Then - statistics show configuration, not request counts
        ShardDataSourceRouter.RoutingStatistics stats = shardAwareDataSourceDelegate.getRoutingStatistics();
        assertEquals(2, stats.totalShards());
        assertEquals(1, stats.shardsWithReplicas());
        assertEquals(1, stats.getShardsWithoutReplicas());
    }

    @Test
    void testMultipleThreadsRoutingConcurrently() throws InterruptedException {
        // Test thread safety of routing
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "shard1", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // Create multiple threads that perform routing
        Thread[] threads = new Thread[5];
        DataSource[] results = new DataSource[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId);
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All results should be the same data source
        for (DataSource result : results) {
            assertEquals(shard1MasterDataSource, result);
        }

        // Verify lookup was called for each thread
        verify(shardLookupService, times(5)).findShardByTenantId(tenantId);
    }

    @Test
    void testMultipleLookups() {
        // Test that multiple lookups for same tenant work correctly
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "shard1", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // Make multiple requests
        DataSource result1 = shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId);
        DataSource result2 = shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId);
        DataSource result3 = shardAwareDataSourceDelegate.routeToShardedDataSource(tenantId);

        // All should return same data source
        assertEquals(shard1MasterDataSource, result1);
        assertEquals(shard1MasterDataSource, result2);
        assertEquals(shard1MasterDataSource, result3);

        // Lookup service should be called for each request (no internal caching in router)
        verify(shardLookupService, times(3)).findShardByTenantId(tenantId);
    }
}