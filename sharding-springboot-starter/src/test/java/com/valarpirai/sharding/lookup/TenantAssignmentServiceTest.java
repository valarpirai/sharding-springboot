package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.config.ShardConfig;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import com.valarpirai.sharding.exception.ShardLookupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantAssignmentServiceTest {

    private ITenantShardMappingRepo mockRepo;
    private ShardConfigService shardConfigService;
    private TenantAssignmentService service;

    @BeforeEach
    void setUp() {
        mockRepo = mock(ITenantShardMappingRepo.class);

        ShardingConfigProperties props = new ShardingConfigProperties();
        ShardConfig shard1 = new ShardConfig();
        shard1.setStatus("ACTIVE");
        shard1.setRegion("us-east-1");
        ShardConfig shard2 = new ShardConfig();
        shard2.setStatus("ACTIVE");
        shard2.setRegion("us-west-2");
        props.setShards(Map.of("shard1", shard1, "shard2", shard2));

        shardConfigService = new ShardConfigService(props);
        service = new TenantAssignmentService(mockRepo, shardConfigService);
    }

    @Test
    void getShardForTenant_returnsShardId_forActiveTenant() {
        TenantShardMapping mapping = new TenantShardMapping(1L, "shard1", "us-east-1", "ACTIVE");
        when(mockRepo.findShardByTenantId(1L)).thenReturn(Optional.of(mapping));

        assertEquals(Optional.of("shard1"), service.getShardForTenant(1L));
    }

    @Test
    void getShardForTenant_returnsEmpty_forInactiveTenant() {
        TenantShardMapping mapping = new TenantShardMapping(1L, "shard1", "us-east-1", "INACTIVE");
        when(mockRepo.findShardByTenantId(1L)).thenReturn(Optional.of(mapping));

        assertTrue(service.getShardForTenant(1L).isEmpty());
    }

    @Test
    void getShardForTenant_returnsEmpty_forNullTenantId() {
        assertTrue(service.getShardForTenant(null).isEmpty());
        verifyNoInteractions(mockRepo);
    }

    @Test
    void tenantExists_true_whenMappingPresent() {
        when(mockRepo.findShardByTenantId(5L))
                .thenReturn(Optional.of(new TenantShardMapping(5L, "shard1", null, "ACTIVE")));
        assertTrue(service.tenantExists(5L));
    }

    @Test
    void tenantExists_false_forNull() {
        assertFalse(service.tenantExists(null));
    }

    @Test
    void assignTenantToLatestShard_createsMapping() {
        when(mockRepo.getLatestShardId()).thenReturn("shard1");
        TenantShardMapping expected = new TenantShardMapping(42L, "shard1", "us-east-1", "ACTIVE");
        when(mockRepo.createMapping(42L, "shard1", "us-east-1")).thenReturn(expected);

        TenantShardMapping result = service.assignTenantToLatestShard(42L);
        assertEquals("shard1", result.getShardId());
        verify(mockRepo).createMapping(42L, "shard1", "us-east-1");
    }

    @Test
    void assignTenantToLatestShard_throwsOnNullTenantId() {
        assertThrows(IllegalArgumentException.class, () -> service.assignTenantToLatestShard(null));
    }

    @Test
    void assignTenantToShard_throwsWhenShardNotConfigured() {
        assertThrows(ShardLookupException.class, () -> service.assignTenantToShard(1L, "ghost"));
    }

    @Test
    void assignTenantToShard_throwsOnNullArgs() {
        assertThrows(IllegalArgumentException.class, () -> service.assignTenantToShard(null, "shard1"));
        assertThrows(IllegalArgumentException.class, () -> service.assignTenantToShard(1L, null));
    }

    @Test
    void getTenantDistribution_groupsByShardId() {
        List<TenantShardMapping> mappings = List.of(
                new TenantShardMapping(1L, "shard1", null, "ACTIVE"),
                new TenantShardMapping(2L, "shard1", null, "ACTIVE"),
                new TenantShardMapping(3L, "shard2", null, "ACTIVE"),
                new TenantShardMapping(4L, "shard1", null, "INACTIVE")
        );
        when(mockRepo.findAllMappings()).thenReturn(mappings);

        Map<String, Long> dist = service.getTenantDistribution();
        assertEquals(2L, dist.get("shard1"));
        assertEquals(1L, dist.get("shard2"));
        assertNull(dist.get("shard3"));
    }

    @Test
    void getTenantsInShard_returnsOnlyActiveTenants() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                new TenantShardMapping(1L, "shard1", null, "ACTIVE"),
                new TenantShardMapping(2L, "shard1", null, "INACTIVE"),
                new TenantShardMapping(3L, "shard2", null, "ACTIVE")
        ));

        List<Long> tenants = service.getTenantsInShard("shard1");
        assertEquals(List.of(1L), tenants);
    }

    @Test
    void getTenantsInShard_returnsEmptyForNullShardId() {
        assertTrue(service.getTenantsInShard(null).isEmpty());
    }

    @Test
    void getShardStatistics_countsCorrectly() {
        when(mockRepo.findAllMappings()).thenReturn(List.of(
                new TenantShardMapping(1L, "shard1", null, "ACTIVE"),
                new TenantShardMapping(2L, "shard2", null, "ACTIVE")
        ));

        ShardStatistics stats = service.getShardStatistics();
        assertEquals(2, stats.totalShards());
        assertEquals(2, stats.activeShards());
        assertEquals(2, stats.totalTenants());
    }
}
