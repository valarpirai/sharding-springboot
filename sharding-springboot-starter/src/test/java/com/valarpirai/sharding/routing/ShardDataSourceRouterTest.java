package com.valarpirai.sharding.routing;

import com.valarpirai.sharding.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ShardDataSourceRouterTest {

    @Mock private DataSource globalDataSource;
    @Mock private DataSource shard1MasterDataSource;
    @Mock private DataSource shard1ReplicaDataSource;
    @Mock private DataSource shard2MasterDataSource;

    private ShardDataSourceRouter router;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        Map<String, ShardDataSources> shardDataSources = new HashMap<>();

        ShardDataSources shard1 = new ShardDataSources("shard1", shard1MasterDataSource);
        shard1.addReplica(shard1ReplicaDataSource);
        shardDataSources.put("shard1", shard1);

        ShardDataSources shard2 = new ShardDataSources("shard2", shard2MasterDataSource);
        shardDataSources.put("shard2", shard2);

        router = new ShardDataSourceRouter(shardDataSources, globalDataSource);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getGlobalDataSource_returnsGlobal() {
        assertEquals(globalDataSource, router.getGlobalDataSource());
    }

    @Test
    void getShardDataSource_writeOperation_returnsMaster() {
        assertEquals(shard1MasterDataSource, router.getShardDataSource("shard1", false));
    }

    @Test
    void getShardDataSource_readOperation_withReplica_returnsReplica() {
        assertEquals(shard1ReplicaDataSource, router.getShardDataSource("shard1", true));
    }

    @Test
    void getShardDataSource_readOperation_noReplicas_fallsBackToMaster() {
        assertEquals(shard2MasterDataSource, router.getShardDataSource("shard2", true));
    }

    @Test
    void getShardDataSource_unknownShard_throws() {
        assertThrows(Exception.class, () -> router.getShardDataSource("unknown", false));
    }

    @Test
    void isShardAvailable_knownShards_returnsTrue() {
        assertTrue(router.isShardAvailable("shard1"));
        assertTrue(router.isShardAvailable("shard2"));
    }

    @Test
    void isShardAvailable_unknownShard_returnsFalse() {
        assertFalse(router.isShardAvailable("unknown"));
    }

    @Test
    void getRoutingStatistics_returnsCorrectCounts() {
        ShardDataSourceRouter.RoutingStatistics stats = router.getRoutingStatistics();
        assertEquals(2, stats.totalShards());
        assertEquals(1, stats.shardsWithReplicas());
        assertEquals(1, stats.getShardsWithoutReplicas());
    }

    @Test
    void multipleThreads_getShardDataSource_returnsConsistentResults() throws InterruptedException {
        Thread[] threads = new Thread[5];
        DataSource[] results = new DataSource[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> results[index] = router.getShardDataSource("shard1", false));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        for (DataSource result : results) {
            assertEquals(shard1MasterDataSource, result);
        }
    }
}
