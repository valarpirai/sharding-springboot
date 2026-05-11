package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.context.TenantContext;
import com.valarpirai.sharding.context.TenantInfo;
import com.valarpirai.sharding.routing.ShardDataSourceRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShardResolutionServiceTest {

    private ITenantShardMappingReadRepo mockRepo;
    private ShardDataSourceRouter mockDelegate;
    private DataSource mockDataSource;
    private ShardResolutionService service;

    @BeforeEach
    void setUp() {
        mockRepo = mock(ITenantShardMappingReadRepo.class);
        mockDelegate = mock(ShardDataSourceRouter.class);
        mockDataSource = mock(DataSource.class);
        service = new ShardResolutionService(mockRepo, mockDelegate);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void resolveTenantInfo_returnsPopulatedTenantInfo() {
        TenantShardMapping mapping = new TenantShardMapping(1L, "shard1", "us-east-1", "ACTIVE");
        when(mockRepo.findShardByTenantId(1L)).thenReturn(Optional.of(mapping));
        when(mockDelegate.getShardDataSource("shard1", false)).thenReturn(mockDataSource);

        Optional<TenantInfo> result = service.resolveTenantInfo(1L, false);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().tenantId());
        assertEquals("shard1", result.get().shardId());
        assertFalse(result.get().readOnlyMode());
        assertSame(mockDataSource, result.get().shardDataSource());
    }

    @Test
    void resolveTenantInfo_returnsEmpty_forNullTenantId() {
        assertTrue(service.resolveTenantInfo(null, false).isEmpty());
        verifyNoInteractions(mockRepo);
    }

    @Test
    void resolveTenantInfo_returnsEmpty_whenNoMapping() {
        when(mockRepo.findShardByTenantId(99L)).thenReturn(Optional.empty());
        assertTrue(service.resolveTenantInfo(99L, false).isEmpty());
    }

    @Test
    void resolveTenantInfo_returnsEmpty_forInactiveMapping() {
        TenantShardMapping inactive = new TenantShardMapping(2L, "shard1", null, "INACTIVE");
        when(mockRepo.findShardByTenantId(2L)).thenReturn(Optional.of(inactive));

        assertTrue(service.resolveTenantInfo(2L, false).isEmpty());
        verifyNoInteractions(mockDelegate);
    }

    @Test
    void resolveTenantInfo_propagatesReadOnlyFlag() {
        TenantShardMapping mapping = new TenantShardMapping(3L, "shard2", null, "ACTIVE");
        when(mockRepo.findShardByTenantId(3L)).thenReturn(Optional.of(mapping));
        when(mockDelegate.getShardDataSource("shard2", true)).thenReturn(mockDataSource);

        Optional<TenantInfo> result = service.resolveTenantInfo(3L, true);
        assertTrue(result.isPresent());
        assertTrue(result.get().readOnlyMode());
        verify(mockDelegate).getShardDataSource("shard2", true);
    }

    @Test
    void resolveTenantInfo_returnsEmpty_onDelegateException() {
        TenantShardMapping mapping = new TenantShardMapping(4L, "shard1", null, "ACTIVE");
        when(mockRepo.findShardByTenantId(4L)).thenReturn(Optional.of(mapping));
        when(mockDelegate.getShardDataSource("shard1", false)).thenThrow(new RuntimeException("pool exhausted"));

        assertTrue(service.resolveTenantInfo(4L, false).isEmpty());
    }

    @Test
    void resolveAndSetTenantContext_returnsTrueAndSetsTenantContext() {
        TenantShardMapping mapping = new TenantShardMapping(5L, "shard1", null, "ACTIVE");
        when(mockRepo.findShardByTenantId(5L)).thenReturn(Optional.of(mapping));
        when(mockDelegate.getShardDataSource("shard1", false)).thenReturn(mockDataSource);

        assertTrue(service.resolveAndSetTenantContext(5L, false));
        assertNotNull(TenantContext.getTenantInfo());
        assertEquals(5L, TenantContext.getCurrentTenantId());
    }

    @Test
    void resolveAndSetTenantContext_returnsFalse_whenNoMapping() {
        when(mockRepo.findShardByTenantId(99L)).thenReturn(Optional.empty());
        assertFalse(service.resolveAndSetTenantContext(99L, false));
        assertNull(TenantContext.getTenantInfo());
    }
}
