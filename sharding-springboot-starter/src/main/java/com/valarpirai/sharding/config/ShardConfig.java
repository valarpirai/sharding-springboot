package com.valarpirai.sharding.config;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for a single shard including master and replicas.
 */
@Data
public class ShardConfig {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private String shardId;
    private DatabaseConfig master;
    private Map<String, DatabaseConfig> replicas = new HashMap<>();
    private HikariConfigProperties hikari = new HikariConfigProperties();
    private Boolean latest = false;
    private String region;
    private String status = STATUS_ACTIVE;

    public boolean isActive() {
        return STATUS_ACTIVE.equalsIgnoreCase(status);
    }
}