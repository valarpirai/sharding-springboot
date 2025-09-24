package com.valarpirai.sharding.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.valarpirai.sharding.lookup.TenantShardMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for caching tenant-shard mappings.
 * Supports both Caffeine (in-memory) and Redis (distributed) caching.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfiguration.class);

    public static final String TENANT_SHARD_CACHE = "tenantShardMappings";

    private final ShardingConfigProperties shardingProperties;

    public CacheConfiguration(ShardingConfigProperties shardingProperties) {
        this.shardingProperties = shardingProperties;
    }

    /**
     * Cache manager configuration based on the configured cache type.
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        ShardingConfigProperties.CacheConfig cacheConfig = shardingProperties.getCache();

        if (!cacheConfig.isEnabled()) {
            logger.info("Caching is disabled for tenant-shard mappings");
            return new NoOpCacheManager();
        }

        switch (cacheConfig.getType()) {
            case CAFFEINE:
                return caffeineCacheManager(cacheConfig);
            case REDIS:
                return redisCacheManager(cacheConfig);
            default:
                logger.warn("Unknown cache type: {}, falling back to Caffeine", cacheConfig.getType());
                return caffeineCacheManager(cacheConfig);
        }
    }

    /**
     * Caffeine-based in-memory cache manager.
     */
    private CacheManager caffeineCacheManager(ShardingConfigProperties.CacheConfig cacheConfig) {
        logger.info("Configuring Caffeine cache for tenant-shard mappings: TTL={}h, MaxSize={}",
                   cacheConfig.getTtlHours(), cacheConfig.getMaxSize());

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(TENANT_SHARD_CACHE);

        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
                .expireAfterWrite(cacheConfig.getTtlHours(), TimeUnit.HOURS)
                .maximumSize(cacheConfig.getMaxSize());

        if (cacheConfig.isRecordStats()) {
            caffeineBuilder.recordStats();
            logger.debug("Caffeine cache statistics recording enabled");
        }

        cacheManager.setCaffeine(caffeineBuilder);
        cacheManager.setAllowNullValues(false);

        return cacheManager;
    }

    /**
     * Redis-based distributed cache manager.
     */
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    private CacheManager redisCacheManager(ShardingConfigProperties.CacheConfig cacheConfig) {
        try {
            logger.info("Configuring Redis cache for tenant-shard mappings: TTL={}h, Host={}:{}",
                       cacheConfig.getTtlHours(), cacheConfig.getRedisHost(), cacheConfig.getRedisPort());

            RedisConnectionFactory connectionFactory = redisConnectionFactory(cacheConfig);

            RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(cacheConfig.getTtlHours()))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                            new Jackson2JsonRedisSerializer<>(TenantShardMapping.class)))
                    .disableCachingNullValues();

            if (cacheConfig.getRedisKeyPrefix() != null && !cacheConfig.getRedisKeyPrefix().isEmpty()) {
                redisCacheConfiguration = redisCacheConfiguration.prefixCacheNameWith(cacheConfig.getRedisKeyPrefix());
            }

            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(redisCacheConfiguration)
                    .build();

        } catch (Exception e) {
            logger.error("Failed to configure Redis cache, falling back to Caffeine: {}", e.getMessage());
            return caffeineCacheManager(cacheConfig);
        }
    }

    /**
     * Redis connection factory for cache.
     */
    @Bean
    @ConditionalOnProperty(name = "app.sharding.cache.type", havingValue = "redis")
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory")
    @ConditionalOnMissingBean
    public RedisConnectionFactory redisConnectionFactory() {
        return redisConnectionFactory(shardingProperties.getCache());
    }

    private RedisConnectionFactory redisConnectionFactory(ShardingConfigProperties.CacheConfig cacheConfig) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(cacheConfig.getRedisHost());
        redisConfig.setPort(cacheConfig.getRedisPort());
        redisConfig.setDatabase(cacheConfig.getRedisDatabase());

        if (cacheConfig.getRedisPassword() != null && !cacheConfig.getRedisPassword().isEmpty()) {
            redisConfig.setPassword(cacheConfig.getRedisPassword());
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.setValidateConnection(true);

        return factory;
    }

    /**
     * Redis template for manual cache operations (optional).
     */
    @Bean
    @ConditionalOnProperty(name = "app.sharding.cache.type", havingValue = "redis")
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @ConditionalOnMissingBean
    public RedisTemplate<String, TenantShardMapping> tenantShardMappingRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, TenantShardMapping> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(TenantShardMapping.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(TenantShardMapping.class));
        template.afterPropertiesSet();
        return template;
    }
}