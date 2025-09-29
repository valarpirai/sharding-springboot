package com.valarpirai.sharding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for dual DataSource setup package organization.
 */
@Data
@ConfigurationProperties(prefix = "app.sharding.dual-datasource")
public class DualDataSourceProperties {

    /**
     * Base package for global (non-sharded) repositories.
     */
    private String globalRepositoryBasePackage = "**.repository.global";

    /**
     * Base package for sharded repositories.
     */
    private String shardedRepositoryBasePackage = "**.repository.sharded";

    /**
     * Base package for global entities.
     */
    private String globalEntityBasePackage = "**.entity.global";

    /**
     * Base package for sharded entities.
     */
    private String shardedEntityBasePackage = "**.entity.sharded";

    /**
     * Whether dual DataSource configuration is enabled.
     */
    private boolean enabled = true;
}