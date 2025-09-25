package com.valarpirai.sharding.cache;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.valarpirai.sharding.config.CacheConfiguration;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for monitoring and reporting cache statistics.
 * Provides metrics for both Caffeine and Redis cache implementations.
 */
@Service
public class CacheStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(CacheStatisticsService.class);

    private final CacheManager cacheManager;
    private final ShardingConfigProperties shardingProperties;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public CacheStatisticsService(CacheManager cacheManager, ShardingConfigProperties shardingProperties) {
        this.cacheManager = cacheManager;
        this.shardingProperties = shardingProperties;
    }

    /**
     * Get comprehensive cache statistics.
     */
    public CacheStatistics getCacheStatistics() {
        Cache cache = cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE);
        if (cache == null) {
            return createEmptyStatistics();
        }

        ShardingConfigProperties.CacheConfig cacheConfig = shardingProperties.getCache();

        if (cache instanceof CaffeineCache) {
            return getCaffeineStatistics((CaffeineCache) cache, cacheConfig);
        } else if (cache instanceof RedisCache) {
            return getRedisStatistics((RedisCache) cache, cacheConfig);
        } else {
            return createBasicStatistics(cacheConfig);
        }
    }

    /**
     * Get Caffeine-specific cache statistics.
     */
    private CacheStatistics getCaffeineStatistics(CaffeineCache caffeineCache, ShardingConfigProperties.CacheConfig cacheConfig) {
        try {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            CacheStats stats = nativeCache.stats();

            CacheStatistics cacheStats = new CacheStatistics();
            cacheStats.setCacheType("CAFFEINE");
            cacheStats.setEnabled(cacheConfig.isEnabled());
            cacheStats.setTtlHours(cacheConfig.getTtlHours());
            cacheStats.setMaxSize(cacheConfig.getMaxSize());

            // Caffeine-specific metrics
            cacheStats.setSize(nativeCache.estimatedSize());
            cacheStats.setHitCount(stats.hitCount());
            cacheStats.setMissCount(stats.missCount());
            cacheStats.setRequestCount(stats.requestCount());
            cacheStats.setHitRate(stats.hitRate());
            cacheStats.setEvictionCount(stats.evictionCount());
            cacheStats.setAverageLoadTime(stats.averageLoadPenalty());

            Map<String, Object> detailedStats = new HashMap<>();
            detailedStats.put("loadCount", stats.loadCount());
            // detailedStats.put("loadExceptionCount", stats.loadExceptionCount()); // Method not available in this Caffeine version
            // detailedStats.put("totalLoadTime", stats.totalLoadPenalty()); // Method not available in this Caffeine version
            detailedStats.put("evictionWeight", stats.evictionWeight());
            cacheStats.setDetailedStats(detailedStats);

            return cacheStats;

        } catch (Exception e) {
            logger.error("Error getting Caffeine cache statistics", e);
            return createBasicStatistics(cacheConfig);
        }
    }

    /**
     * Get Redis-specific cache statistics.
     */
    private CacheStatistics getRedisStatistics(RedisCache redisCache, ShardingConfigProperties.CacheConfig cacheConfig) {
        try {
            CacheStatistics cacheStats = new CacheStatistics();
            cacheStats.setCacheType("REDIS");
            cacheStats.setEnabled(cacheConfig.isEnabled());
            cacheStats.setTtlHours(cacheConfig.getTtlHours());
            cacheStats.setMaxSize(-1); // Redis doesn't have a fixed max size

            // Basic metrics from our tracking
            cacheStats.setHitCount(cacheHits.get());
            cacheStats.setMissCount(cacheMisses.get());
            cacheStats.setRequestCount(totalRequests.get());

            if (totalRequests.get() > 0) {
                cacheStats.setHitRate((double) cacheHits.get() / totalRequests.get());
            }

            // Redis connection info
            Map<String, Object> detailedStats = new HashMap<>();
            detailedStats.put("redisHost", cacheConfig.getRedisHost());
            detailedStats.put("redisPort", cacheConfig.getRedisPort());
            detailedStats.put("redisDatabase", cacheConfig.getRedisDatabase());
            detailedStats.put("keyPrefix", cacheConfig.getRedisKeyPrefix());

            // Try to get Redis-specific metrics
            try {
                // RedisConnectionFactory connectionFactory = redisCache.getCacheWriter().getConnectionFactory(); // Method not available in this Spring Data Redis version
                // Note: Redis-specific metrics temporarily disabled due to API incompatibility
                /*
                if (connectionFactory != null) {
                    RedisConnection connection = connectionFactory.getConnection();
                    if (connection != null) {
                        // Get Redis server info if available
                        // Note: This might not work in all Redis configurations
                        detailedStats.put("connected", true);
                        connection.close();
                    }
                }
                */
            } catch (Exception e) {
                logger.debug("Could not get Redis connection info: {}", e.getMessage());
                detailedStats.put("connected", false);
            }

            cacheStats.setDetailedStats(detailedStats);
            return cacheStats;

        } catch (Exception e) {
            logger.error("Error getting Redis cache statistics", e);
            return createBasicStatistics(cacheConfig);
        }
    }

    /**
     * Create basic statistics when detailed stats are not available.
     */
    private CacheStatistics createBasicStatistics(ShardingConfigProperties.CacheConfig cacheConfig) {
        CacheStatistics cacheStats = new CacheStatistics();
        cacheStats.setCacheType(cacheConfig.getType().toString());
        cacheStats.setEnabled(cacheConfig.isEnabled());
        cacheStats.setTtlHours(cacheConfig.getTtlHours());
        cacheStats.setMaxSize(cacheConfig.getMaxSize());

        // Use our basic tracking
        cacheStats.setHitCount(cacheHits.get());
        cacheStats.setMissCount(cacheMisses.get());
        cacheStats.setRequestCount(totalRequests.get());

        if (totalRequests.get() > 0) {
            cacheStats.setHitRate((double) cacheHits.get() / totalRequests.get());
        }

        return cacheStats;
    }

    /**
     * Create empty statistics when cache is disabled.
     */
    private CacheStatistics createEmptyStatistics() {
        CacheStatistics cacheStats = new CacheStatistics();
        cacheStats.setCacheType("DISABLED");
        cacheStats.setEnabled(false);
        return cacheStats;
    }

    /**
     * Record a cache hit for manual tracking.
     */
    public void recordHit() {
        totalRequests.incrementAndGet();
        cacheHits.incrementAndGet();
    }

    /**
     * Record a cache miss for manual tracking.
     */
    public void recordMiss() {
        totalRequests.incrementAndGet();
        cacheMisses.incrementAndGet();
    }

    /**
     * Clear cache and reset statistics.
     */
    public void clearCache() {
        Cache cache = cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE);
        if (cache != null) {
            cache.clear();
            logger.info("Cleared tenant-shard mapping cache");
        }

        // Reset manual tracking
        totalRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    /**
     * Cache statistics data structure.
     */
    @Data
    public static class CacheStatistics {
        private String cacheType;
        private boolean enabled;
        private int ttlHours;
        private int maxSize;
        private long size;
        private long hitCount;
        private long missCount;
        private long requestCount;
        private double hitRate;
        private long evictionCount;
        private double averageLoadTime;
        private Map<String, Object> detailedStats = new HashMap<>();
    }
}