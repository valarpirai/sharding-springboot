package com.valarpirai.sharding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for dual DataSource setup package organization.
 */
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

    public String getGlobalRepositoryBasePackage() {
        return globalRepositoryBasePackage;
    }

    public void setGlobalRepositoryBasePackage(String globalRepositoryBasePackage) {
        this.globalRepositoryBasePackage = globalRepositoryBasePackage;
    }

    public String getShardedRepositoryBasePackage() {
        return shardedRepositoryBasePackage;
    }

    public void setShardedRepositoryBasePackage(String shardedRepositoryBasePackage) {
        this.shardedRepositoryBasePackage = shardedRepositoryBasePackage;
    }

    public String getGlobalEntityBasePackage() {
        return globalEntityBasePackage;
    }

    public void setGlobalEntityBasePackage(String globalEntityBasePackage) {
        this.globalEntityBasePackage = globalEntityBasePackage;
    }

    public String getShardedEntityBasePackage() {
        return shardedEntityBasePackage;
    }

    public void setShardedEntityBasePackage(String shardedEntityBasePackage) {
        this.shardedEntityBasePackage = shardedEntityBasePackage;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}