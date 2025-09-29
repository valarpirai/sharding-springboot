package com.valarpirai.sharding.config;

import com.valarpirai.sharding.routing.ShardedRoutingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Auto-configuration for sharding DataSource setup:
 * - Global DataSource for non-sharded entities
 * - Sharded DataSource that routes based on TenantContext
 */
@Configuration
@ConditionalOnProperty(prefix = "app.sharding.dual-datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DualDataSourceProperties.class)
public class ShardingDataSourceAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ShardingDataSourceAutoConfiguration.class);

    private final DualDataSourceProperties properties;

    public ShardingDataSourceAutoConfiguration(DualDataSourceProperties properties) {
        this.properties = properties;
        logger.info("Dual DataSource auto-configuration enabled with packages - Global: {}, Sharded: {}",
                properties.getGlobalRepositoryBasePackage(), properties.getShardedRepositoryBasePackage());
    }

    /**
     * Global DataSource configuration for non-sharded entities.
     * Uses the existing global DataSource from sharding configuration.
     */
    @Configuration
    @EnableJpaRepositories(
        basePackages = "#{@dualDataSourceProperties.globalRepositoryBasePackage}",
        entityManagerFactoryRef = "globalEntityManagerFactory",
        transactionManagerRef = "globalTransactionManager"
    )
    public static class GlobalDataSourceConfig {

        private final DualDataSourceProperties properties;

        public GlobalDataSourceConfig(DualDataSourceProperties properties) {
            this.properties = properties;
        }

        @Bean(name = "globalEntityManagerFactory")
        public LocalContainerEntityManagerFactoryBean globalEntityManagerFactory(
                EntityManagerFactoryBuilder builder,
                @Qualifier("globalDataSource") DataSource globalDataSource,
                JpaProperties jpaProperties) {

            return builder
                .dataSource(globalDataSource)
                .packages(properties.getGlobalEntityBasePackage())
                .persistenceUnit("global")
                .properties(jpaProperties.getProperties())
                .build();
        }

        @Bean(name = "globalTransactionManager")
        public PlatformTransactionManager globalTransactionManager(
                @Qualifier("globalEntityManagerFactory") LocalContainerEntityManagerFactoryBean globalEntityManagerFactory) {
            return new JpaTransactionManager(globalEntityManagerFactory.getObject());
        }
    }

    /**
     * Sharded DataSource configuration for tenant-specific entities.
     * Routes to the appropriate shard based on TenantContext.
     */
    @Configuration
    @EnableJpaRepositories(
        basePackages = "#{@dualDataSourceProperties.shardedRepositoryBasePackage}",
        entityManagerFactoryRef = "shardedEntityManagerFactory",
        transactionManagerRef = "shardedTransactionManager"
    )
    public static class ShardedDataSourceConfig {

        private final DualDataSourceProperties properties;

        public ShardedDataSourceConfig(DualDataSourceProperties properties) {
            this.properties = properties;
        }

        @Bean(name = "shardedDataSource")
        @Primary
        public DataSource shardedDataSource(@Qualifier("globalDataSource") DataSource globalDataSource) {
            return new ShardedRoutingDataSource(globalDataSource);
        }

        @Bean(name = "shardedEntityManagerFactory")
        @Primary
        public LocalContainerEntityManagerFactoryBean shardedEntityManagerFactory(
                EntityManagerFactoryBuilder builder,
                @Qualifier("shardedDataSource") DataSource shardedDataSource,
                JpaProperties jpaProperties) {

            return builder
                .dataSource(shardedDataSource)
                .packages(properties.getShardedEntityBasePackage())
                .persistenceUnit("sharded")
                .properties(jpaProperties.getProperties())
                .build();
        }

        @Bean(name = "shardedTransactionManager")
        @Primary
        public PlatformTransactionManager shardedTransactionManager(
                @Qualifier("shardedEntityManagerFactory") LocalContainerEntityManagerFactoryBean shardedEntityManagerFactory) {
            return new JpaTransactionManager(shardedEntityManagerFactory.getObject());
        }
    }

}