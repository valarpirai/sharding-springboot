package com.valarpirai.sharding.lookup;

import com.valarpirai.sharding.config.ShardConfig;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Queries shard configuration properties.
 * Has no I/O dependencies — reads only from ShardingConfigProperties.
 */
@Component
public class ShardConfigService {

    private final ShardingConfigProperties shardingConfig;

    public ShardConfigService(ShardingConfigProperties shardingConfig) {
        this.shardingConfig = shardingConfig;
    }

    public Optional<ShardConfig> getShardConfig(String shardId) {
        if (shardId == null) return Optional.empty();
        return Optional.ofNullable(shardingConfig.getShards().get(shardId));
    }

    public boolean isShardConfigured(String shardId) {
        return shardId != null && shardingConfig.getShards().containsKey(shardId);
    }

    public Set<String> getAllShardIds() {
        return shardingConfig.getShards().keySet();
    }

    public Set<String> getActiveShardIds() {
        return shardingConfig.getShards().entrySet().stream()
                .filter(e -> e.getValue().isActive())
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
