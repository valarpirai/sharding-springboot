package com.valarpirai.sharding.iterator;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.lookup.ITenantShardMappingReadRepo;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import com.valarpirai.sharding.routing.ShardAwareDataSourceDelegate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantIteratorTest {

    private ITenantShardMappingReadRepo mockRepo;
    private ShardAwareDataSourceDelegate mockDelegate;
    private DataSource mockDataSource;
    private TenantIterator iterator;

    @BeforeEach
    void setUp() {
        mockRepo = mock(ITenantShardMappingReadRepo.class);
        mockDelegate = mock(ShardAwareDataSourceDelegate.class);
        mockDataSource = mock(DataSource.class);
        iterator = new TenantIterator(mockRepo, mockDelegate);
        when(mockDelegate.getShardDataSource(anyString(), anyBoolean())).thenReturn(mockDataSource);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    // ── getActiveMappings (indirect, via processAllTenants) ─────────────────

    @Test
    void processAllTenants_skipsInactiveTenants() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                active(1L, "shard1"),
                inactive(2L, "shard1"),
                active(3L, "shard1")
        ));

        List<Long> processed = new ArrayList<>();
        iterator.processAllTenants(processed::add);

        assertEquals(List.of(1L, 3L), processed);
    }

    @Test
    void processAllTenants_emptyWhenNoActiveTenants() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(inactive(1L, "s1")));
        List<Long> processed = new ArrayList<>();
        iterator.processAllTenants(processed::add);
        assertTrue(processed.isEmpty());
    }

    // ── sliceIntoBatches (indirect, via processBatches) ─────────────────────

    @Test
    void processAllTenants_respectsBatchSize() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                active(1L, "s1"), active(2L, "s1"), active(3L, "s1"), active(4L, "s1"), active(5L, "s1")
        ));

        AtomicInteger callCount = new AtomicInteger();
        iterator.processAllTenants(id -> callCount.incrementAndGet(), 2);
        assertEquals(5, callCount.get());
    }

    @Test
    void processAllTenants_singleBatchWhenFewerThanBatchSize() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(active(1L, "s1"), active(2L, "s1")));
        List<Long> processed = new ArrayList<>();
        iterator.processAllTenants(processed::add, 10);
        assertEquals(List.of(1L, 2L), processed);
    }

    @Test
    void processAllTenants_throwsOnNonPositiveBatchSize() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(active(1L, "s1")));
        assertThrows(IllegalArgumentException.class,
                () -> iterator.processAllTenants(id -> {}, 0));
        assertThrows(IllegalArgumentException.class,
                () -> iterator.processAllTenants(id -> {}, -1));
    }

    // ── processBatches shared between sync paths ─────────────────────────────

    @Test
    void processTenantsInShard_onlyProcessesTenantInTargetShard() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                active(1L, "shard1"),
                active(2L, "shard2"),
                active(3L, "shard1")
        ));

        List<Long> processed = new ArrayList<>();
        iterator.processTenantsInShard("shard1", processed::add);

        assertEquals(List.of(1L, 3L), processed);
    }

    @Test
    void processTenantsInShard_doesNothingWhenShardEmpty() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(active(1L, "shard1")));
        List<Long> processed = new ArrayList<>();
        iterator.processTenantsInShard("shard2", processed::add);
        assertTrue(processed.isEmpty());
        verifyNoMoreInteractions(mockDelegate);
    }

    @Test
    void processTenantsInShard_throwsOnNullShardId() {
        assertThrows(IllegalArgumentException.class,
                () -> iterator.processTenantsInShard(null, id -> {}));
    }

    // ── async path ───────────────────────────────────────────────────────────

    @Test
    void processAllTenantsAsync_processesAllActiveTenants() throws Exception {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                active(1L, "s1"), active(2L, "s1"), inactive(3L, "s1")
        ));

        List<Long> processed = new ArrayList<>();
        CompletableFuture<Void> future = iterator.processAllTenantsAsync(
                id -> { synchronized (processed) { processed.add(id); } });
        future.get();

        assertEquals(2, processed.size());
        assertTrue(processed.containsAll(List.of(1L, 2L)));
    }

    // ── mapAllTenants ────────────────────────────────────────────────────────

    @Test
    void mapAllTenants_collectsNonNullResults() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                active(1L, "s1"), active(2L, "s1"), active(3L, "s1")
        ));

        List<String> results = iterator.mapAllTenants(id -> id == 2L ? null : "tenant-" + id);
        assertEquals(2, results.size());
        assertTrue(results.containsAll(List.of("tenant-1", "tenant-3")));
    }

    @Test
    void mapAllTenants_tenantContextIsSetDuringMapping() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(active(7L, "shard1")));

        List<Long> contextIds = new ArrayList<>();
        iterator.mapAllTenants(id -> {
            contextIds.add(TenantContext.getCurrentTenantId());
            return id;
        });

        assertEquals(List.of(7L), contextIds);
    }

    // ── createBatchIterator ──────────────────────────────────────────────────

    @Test
    void createBatchIterator_yieldsCorrectBatches() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                active(1L, "s1"), active(2L, "s1"), active(3L, "s1"), active(4L, "s1"), active(5L, "s1")
        ));

        Iterator<List<Long>> it = iterator.createBatchIterator(2);
        assertTrue(it.hasNext());
        assertEquals(List.of(1L, 2L), it.next());
        assertEquals(List.of(3L, 4L), it.next());
        assertEquals(List.of(5L), it.next());
        assertFalse(it.hasNext());
    }

    // ── getProcessingStats ───────────────────────────────────────────────────

    @Test
    void getProcessingStats_countsCorrectly() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                active(1L, "s1"), active(2L, "s1"), inactive(3L, "s1")
        ));

        TenantIterator.TenantProcessingStats stats = iterator.getProcessingStats();
        assertEquals(2, stats.getActiveTenants());
        assertEquals(1, stats.getInactiveTenants());
        assertEquals(3, stats.getTotalTenants());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TenantShardMapping active(Long id, String shard) {
        return new TenantShardMapping(id, shard, null, "ACTIVE");
    }

    private TenantShardMapping inactive(Long id, String shard) {
        return new TenantShardMapping(id, shard, null, "INACTIVE");
    }
}
