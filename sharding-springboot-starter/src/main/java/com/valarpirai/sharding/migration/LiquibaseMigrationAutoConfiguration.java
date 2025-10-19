package com.valarpirai.sharding.migration;

import com.valarpirai.sharding.config.ShardingConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Liquibase migration support.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "liquibase.Liquibase")
@ConditionalOnProperty(prefix = "app.sharding.migration", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({LiquibaseMigrationConfig.class})
public class LiquibaseMigrationAutoConfiguration {

    @Bean
    public MigrationProgressTracker migrationProgressTracker() {
        log.info("Initializing MigrationProgressTracker");
        return new MigrationProgressTracker();
    }

    @Bean
    public MigrationLockManager migrationLockManager() {
        log.info("Initializing MigrationLockManager");
        return new MigrationLockManager();
    }

    @Bean
    public LiquibaseMigrationOrchestrator liquibaseMigrationOrchestrator(
            ShardingConfigProperties shardingProperties,
            MigrationProgressTracker progressTracker,
            LiquibaseMigrationConfig migrationConfig,
            MigrationLockManager lockManager) {
        log.info("Initializing LiquibaseMigrationOrchestrator with strategy: {}",
                 migrationConfig.getDefaultStrategy());
        return new LiquibaseMigrationOrchestrator(
                shardingProperties,
                progressTracker,
                migrationConfig,
                lockManager
        );
    }

    @Bean
    public MigrationValidator migrationValidator() {
        log.info("Initializing MigrationValidator");
        return new MigrationValidator();
    }
}
