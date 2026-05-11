package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.config.ShardConfig;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShardConfigServiceTest {

    private ShardConfigService service;

    @BeforeEach
    void setUp() {
        ShardingConfigProperties props = new ShardingConfigProperties();

        ShardConfig active = new ShardConfig();
        active.setStatus("ACTIVE");

        ShardConfig maintenance = new ShardConfig();
        maintenance.setStatus("MAINTENANCE");

        props.setShards(Map.of("shard1", active, "shard2", maintenance));
        service = new ShardConfigService(props);
    }

    @Test
    void getShardConfig_returnsPresent_forKnownShard() {
        assertTrue(service.getShardConfig("shard1").isPresent());
    }

    @Test
    void getShardConfig_returnsEmpty_forUnknownShard() {
        assertTrue(service.getShardConfig("missing").isEmpty());
    }

    @Test
    void getShardConfig_returnsEmpty_forNull() {
        assertTrue(service.getShardConfig(null).isEmpty());
    }

    @Test
    void isShardConfigured_trueForKnownShard() {
        assertTrue(service.isShardConfigured("shard1"));
    }

    @Test
    void isShardConfigured_falseForUnknownShard() {
        assertFalse(service.isShardConfigured("ghost"));
    }

    @Test
    void isShardConfigured_falseForNull() {
        assertFalse(service.isShardConfigured(null));
    }

    @Test
    void getAllShardIds_containsBothShards() {
        Set<String> ids = service.getAllShardIds();
        assertTrue(ids.containsAll(Set.of("shard1", "shard2")));
    }

    @Test
    void getActiveShardIds_returnsOnlyActiveShards() {
        Set<String> active = service.getActiveShardIds();
        assertTrue(active.contains("shard1"));
        assertFalse(active.contains("shard2"));
    }

    @Test
    void getActiveShardIds_emptyWhenNoActiveShards() {
        ShardingConfigProperties props = new ShardingConfigProperties();
        ShardConfig maint = new ShardConfig();
        maint.setStatus("MAINTENANCE");
        props.setShards(Map.of("s1", maint));
        ShardConfigService s = new ShardConfigService(props);
        assertTrue(s.getActiveShardIds().isEmpty());
    }
}
