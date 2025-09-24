package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.ShardLookupService;
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
class ConnectionRouterTest {

    @Mock
    private ShardLookupService shardLookupService;

    @Mock
    private DataSource globalDataSource;

    @Mock
    private DataSource shard1MasterDataSource;

    @Mock
    private DataSource shard1ReplicaDataSource;

    @Mock
    private DataSource shard2MasterDataSource;

    private Map<String, ShardDataSources> shardDataSources;
    private ConnectionRouter connectionRouter;

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

        connectionRouter = new ConnectionRouter(shardLookupService, shardDataSources, globalDataSource);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testGetDataSourceWithoutTenantContext() {
        // When no tenant context is set, should return global data source
        DataSource result = connectionRouter.getDataSource();
        assertEquals(globalDataSource, result);
    }

    @Test
    void testGetDataSourceWithTenantContextAndValidMapping() {
        // Given
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "shard1", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // When
        TenantContext.setTenantId(tenantId);
        DataSource result = connectionRouter.getDataSource();

        // Then
        assertEquals(shard1MasterDataSource, result);
        verify(shardLookupService).findShardByTenantId(tenantId);
    }

    @Test
    void testGetDataSourceWithTenantContextAndReadOnlyMode() {
        // Given
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "shard1", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // When
        TenantContext.setTenantId(tenantId);
        TenantContext.setReadOnlyMode(true);
        DataSource result = connectionRouter.getDataSource();

        // Then
        // Should return replica data source when in read-only mode
        assertEquals(shard1ReplicaDataSource, result);
        verify(shardLookupService).findShardByTenantId(tenantId);
    }

    @Test
    void testGetDataSourceWithTenantContextButNoMapping() {
        // Given
        Long tenantId = 1001L;
        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.empty());

        // When
        TenantContext.setTenantId(tenantId);

        // Then
        assertThrows(IllegalStateException.class, () -> {
            connectionRouter.getDataSource();
        });

        verify(shardLookupService).findShardByTenantId(tenantId);
    }

    @Test
    void testGetDataSourceWithInvalidShardId() {
        // Given
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "invalid_shard", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // When
        TenantContext.setTenantId(tenantId);

        // Then
        assertThrows(IllegalStateException.class, () -> {
            connectionRouter.getDataSource();
        });
    }

    @Test
    void testGetDataSourceWithReadOnlyModeButNoReplicas() {
        // Given
        Long tenantId = 2001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "shard2", "us-west-2", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        // When
        TenantContext.setTenantId(tenantId);
        TenantContext.setReadOnlyMode(true);
        DataSource result = connectionRouter.getDataSource();

        // Then
        // Should fall back to master when no replicas available
        assertEquals(shard2MasterDataSource, result);
    }

    @Test
    void testIsShardAvailable() {
        // Test with existing shard
        assertTrue(connectionRouter.isShardAvailable("shard1"));
        assertTrue(connectionRouter.isShardAvailable("shard2"));

        // Test with non-existing shard
        assertFalse(connectionRouter.isShardAvailable("invalid_shard"));
        assertFalse(connectionRouter.isShardAvailable(null));
    }

    @Test
    void testGetRoutingStatistics() {
        // Test routing statistics functionality
        ConnectionRouter.RoutingStatistics stats = connectionRouter.getRoutingStatistics();

        assertNotNull(stats);
        assertEquals(0, stats.getTotalRoutingRequests());
        assertEquals(0, stats.getGlobalDatabaseRequests());
        assertEquals(0, stats.getShardRequests());
        assertNotNull(stats.getShardRequestDistribution());
        assertTrue(stats.getShardRequestDistribution().isEmpty());
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

        // When - make some routing requests
        TenantContext.setTenantId(tenantId1);
        connectionRouter.getDataSource();
        TenantContext.clear();

        TenantContext.setTenantId(tenantId2);
        connectionRouter.getDataSource();
        TenantContext.clear();

        // Global request
        connectionRouter.getDataSource();

        // Then
        ConnectionRouter.RoutingStatistics stats = connectionRouter.getRoutingStatistics();
        assertEquals(3, stats.getTotalRoutingRequests());
        assertEquals(1, stats.getGlobalDatabaseRequests());
        assertEquals(2, stats.getShardRequests());

        Map<String, Long> distribution = stats.getShardRequestDistribution();
        assertEquals(1L, distribution.get("shard1"));
        assertEquals(1L, distribution.get("shard2"));
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
                TenantContext.setTenantId(tenantId);
                try {
                    results[index] = connectionRouter.getDataSource();
                } finally {
                    TenantContext.clear();
                }
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
    void testCachedLookups() {
        // Test that multiple lookups for same tenant use cache if available
        Long tenantId = 1001L;
        TenantShardMapping mapping = new TenantShardMapping(tenantId, "shard1", "us-east-1", "ACTIVE");
        mapping.setCreatedAt(java.time.LocalDateTime.now());

        when(shardLookupService.findShardByTenantId(tenantId)).thenReturn(Optional.of(mapping));

        TenantContext.setTenantId(tenantId);

        // Make multiple requests
        DataSource result1 = connectionRouter.getDataSource();
        DataSource result2 = connectionRouter.getDataSource();
        DataSource result3 = connectionRouter.getDataSource();

        // All should return same data source
        assertEquals(shard1MasterDataSource, result1);
        assertEquals(shard1MasterDataSource, result2);
        assertEquals(shard1MasterDataSource, result3);

        // Lookup service should be called for each request (no internal caching in router)
        verify(shardLookupService, times(3)).findShardByTenantId(tenantId);
    }
}