package com.valarpirai.sharding.config;

import com.valarpirai.sharding.iterator.TenantIterator;
import com.valarpirai.sharding.lookup.DatabaseSqlProviderFactory;
import com.valarpirai.sharding.lookup.ShardUtils;
import com.valarpirai.sharding.routing.ShardAwareDataSourceDelegate;
import com.valarpirai.sharding.validation.EntityValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ShardingAutoConfiguration.
 */
class ShardingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShardingAutoConfiguration.class));

    @Test
    void testAutoConfigurationWithMinimalConfiguration() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.global-db.url=jdbc:h2:mem:global_test",
                        "app.sharding.global-db.username=sa",
                        "app.sharding.global-db.password=",
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password=",
                        "app.sharding.dual-datasource.enabled=false"
                )
                .run(context -> {
                    // Verify core beans are created
                    assertThat(context).hasSingleBean(ShardingConfigProperties.class);
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context).hasSingleBean(DatabaseSqlProviderFactory.class);
                    assertThat(context).hasSingleBean(com.valarpirai.sharding.lookup.ITenantShardMappingRepo.class);
                    assertThat(context).hasSingleBean(ShardUtils.class);
                    assertThat(context).hasSingleBean(TenantIterator.class);
                    assertThat(context).hasSingleBean(EntityValidator.class);
                    assertThat(context).hasSingleBean(ShardAwareDataSourceDelegate.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                });
    }

    @Test
    void testAutoConfigurationWithCacheDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.global-db.url=jdbc:h2:mem:global_test",
                        "app.sharding.global-db.username=sa",
                        "app.sharding.global-db.password=",
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password=",
                        "app.sharding.cache.enabled=false",
                        "app.sharding.dual-datasource.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    assertThat(cacheManager.getClass().getSimpleName()).contains("NoOpCacheManager");
                });
    }

    @Test
    void testAutoConfigurationWithCaffeineCache() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.global-db.url=jdbc:h2:mem:global_test",
                        "app.sharding.global-db.username=sa",
                        "app.sharding.global-db.password=",
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password=",
                        "app.sharding.cache.enabled=true",
                        "app.sharding.cache.type=CAFFEINE",
                        "app.sharding.cache.ttl-hours=2",
                        "app.sharding.cache.max-size=5000",
                        "app.sharding.dual-datasource.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    assertThat(cacheManager.getClass().getSimpleName()).contains("CaffeineCacheManager");

                    // Verify configuration properties are loaded
                    ShardingConfigProperties properties = context.getBean(ShardingConfigProperties.class);
                    assertThat(properties.getCache().isEnabled()).isTrue();
                    assertThat(properties.getCache().getType()).isEqualTo(ShardingConfigProperties.CacheType.CAFFEINE);
                    assertThat(properties.getCache().getTtlHours()).isEqualTo(2);
                    assertThat(properties.getCache().getMaxSize()).isEqualTo(5000);
                });
    }

    @Test
    void testAutoConfigurationWithTenantColumnNames() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.global-db.url=jdbc:h2:mem:global_test",
                        "app.sharding.global-db.username=sa",
                        "app.sharding.global-db.password=",
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password=",
                        "app.sharding.tenant-column-names=tenant_id,company_id,org_id",
                        "app.sharding.dual-datasource.enabled=false"
                )
                .run(context -> {
                    ShardingConfigProperties properties = context.getBean(ShardingConfigProperties.class);
                    assertThat(properties.getTenantColumnNames())
                            .containsExactly("tenant_id", "company_id", "org_id");
                });
    }

    @Test
    void testAutoConfigurationWithMultipleShards() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.global-db.url=jdbc:h2:mem:global_test",
                        "app.sharding.global-db.username=sa",
                        "app.sharding.global-db.password=",
                        // Shard 1
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password=",
                        "app.sharding.shards.shard1.replica1.url=jdbc:h2:mem:shard1_replica_test",
                        "app.sharding.shards.shard1.replica1.username=sa",
                        "app.sharding.shards.shard1.replica1.password=",
                        "app.sharding.shards.shard1.latest=true",
                        "app.sharding.shards.shard1.region=us-east-1",
                        // Shard 2
                        "app.sharding.shards.shard2.master.url=jdbc:h2:mem:shard2_test",
                        "app.sharding.shards.shard2.master.username=sa",
                        "app.sharding.shards.shard2.master.password=",
                        "app.sharding.shards.shard2.latest=false",
                        "app.sharding.shards.shard2.region=us-west-2",
                        "app.sharding.dual-datasource.enabled=false"
                )
                .run(context -> {
                    ShardingConfigProperties properties = context.getBean(ShardingConfigProperties.class);
                    assertThat(properties.getShards()).hasSize(2);
                    assertThat(properties.getShards().get("shard1").getLatest()).isTrue();
                    assertThat(properties.getShards().get("shard1").getRegion()).isEqualTo("us-east-1");
                    assertThat(properties.getShards().get("shard2").getLatest()).isFalse();
                    assertThat(properties.getShards().get("shard2").getRegion()).isEqualTo("us-west-2");
                });
    }

    @Test
    void testAutoConfigurationWithHikariSettings() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.global-db.url=jdbc:h2:mem:global_test",
                        "app.sharding.global-db.username=sa",
                        "app.sharding.global-db.password=",
                        "app.sharding.global-db.hikari.maximum-pool-size=15",
                        "app.sharding.global-db.hikari.minimum-idle=3",
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password=",
                        "app.sharding.shards.shard1.hikari.maximum-pool-size=25",
                        "app.sharding.shards.shard1.hikari.minimum-idle=8",
                        "app.sharding.dual-datasource.enabled=false"
                )
                .run(context -> {
                    ShardingConfigProperties properties = context.getBean(ShardingConfigProperties.class);
                    assertThat(properties.getGlobalDb().getHikari().getMaximumPoolSize()).isEqualTo(15);
                    assertThat(properties.getGlobalDb().getHikari().getMinimumIdle()).isEqualTo(3);
                    assertThat(properties.getShards().get("shard1").getHikari().getMaximumPoolSize()).isEqualTo(25);
                    assertThat(properties.getShards().get("shard1").getHikari().getMinimumIdle()).isEqualTo(8);
                });
    }

    @Test
    void testAutoConfigurationFailsWithoutGlobalDatabase() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void testCustomBeansAreNotOverridden() {
        contextRunner
                .withPropertyValues(
                        "app.sharding.global-db.url=jdbc:h2:mem:global_test",
                        "app.sharding.global-db.username=sa",
                        "app.sharding.global-db.password=",
                        "app.sharding.shards.shard1.master.url=jdbc:h2:mem:shard1_test",
                        "app.sharding.shards.shard1.master.username=sa",
                        "app.sharding.shards.shard1.master.password=",
                        "app.sharding.dual-datasource.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EntityValidator.class);
                });
    }
}