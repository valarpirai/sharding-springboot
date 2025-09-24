package com.valarpirai.sharding.config;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for a single shard including master and replicas.
 */
@Data
public class ShardConfigProperties {

    private String shardId;
    private DatabaseConfigProperties master;
    private Map<String, DatabaseConfigProperties> replicas = new HashMap<>();
    private HikariConfigProperties hikari = new HikariConfigProperties();
    private Boolean latest = false;
    private String region;
    private String status = "ACTIVE";
}