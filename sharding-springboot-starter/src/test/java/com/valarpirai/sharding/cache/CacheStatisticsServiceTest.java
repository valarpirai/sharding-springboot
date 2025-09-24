package com.valarpirai.sharding.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.valarpirai.sharding.config.CacheConfiguration;
import com.valarpirai.sharding.config.ShardingConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCache;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CacheStatisticsService.
 */
@ExtendWith(MockitoExtension.class)
class CacheStatisticsServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private ShardingConfigProperties shardingProperties;

    @Mock
    private ShardingConfigProperties.CacheConfig cacheConfig;

    private CacheStatisticsService service;

    @BeforeEach
    void setUp() {
        when(shardingProperties.getCache()).thenReturn(cacheConfig);
        service = new CacheStatisticsService(cacheManager, shardingProperties);
    }

    @Test
    void testGetCacheStatisticsWithNoCache() {
        // Given
        when(cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE)).thenReturn(null);

        // When
        CacheStatisticsService.CacheStatistics stats = service.getCacheStatistics();

        // Then
        assertEquals("DISABLED", stats.getCacheType());
        assertFalse(stats.isEnabled());
    }

    @Test
    void testGetCacheStatisticsWithCaffeineCache() {
        // Given
        Cache<Object, Object> nativeCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build();

        // Add some data to get realistic stats
        nativeCache.put("key1", "value1");
        nativeCache.put("key2", "value2");
        nativeCache.getIfPresent("key1"); // Hit
        nativeCache.getIfPresent("key3"); // Miss

        CaffeineCache caffeineCache = new CaffeineCache("testCache", nativeCache);

        when(cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE)).thenReturn(caffeineCache);
        when(cacheConfig.isEnabled()).thenReturn(true);
        when(cacheConfig.getTtlHours()).thenReturn(1);
        when(cacheConfig.getMaxSize()).thenReturn(100);

        // When
        CacheStatisticsService.CacheStatistics stats = service.getCacheStatistics();

        // Then
        assertEquals("CAFFEINE", stats.getCacheType());
        assertTrue(stats.isEnabled());
        assertEquals(1, stats.getTtlHours());
        assertEquals(100, stats.getMaxSize());
        assertEquals(2, stats.getSize()); // 2 entries
        assertEquals(1, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(2, stats.getRequestCount());
        assertEquals(0.5, stats.getHitRate(), 0.01);

        assertNotNull(stats.getDetailedStats());
        assertTrue(stats.getDetailedStats().containsKey("loadCount"));
    }

    @Test
    void testGetCacheStatisticsWithRedisCache() {
        // Given
        // Create a mock RedisCache - we can't easily create a real one in unit tests
        org.springframework.cache.Cache redisCache = mock(org.springframework.cache.Cache.class);
        when(redisCache.getName()).thenReturn("tenantShardMappings");
        // RedisCache doesn't extend CaffeineCache, so it will fall through to Redis handling

        when(cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE)).thenReturn(redisCache);
        when(cacheConfig.isEnabled()).thenReturn(true);
        when(cacheConfig.getType()).thenReturn(ShardingConfigProperties.CacheType.REDIS);
        when(cacheConfig.getTtlHours()).thenReturn(2);
        when(cacheConfig.getRedisHost()).thenReturn("localhost");
        when(cacheConfig.getRedisPort()).thenReturn(6379);
        when(cacheConfig.getRedisDatabase()).thenReturn(0);
        when(cacheConfig.getRedisKeyPrefix()).thenReturn("sharding:tenant:");

        // Add some manual tracking
        service.recordHit();
        service.recordMiss();
        service.recordHit();

        // When
        CacheStatisticsService.CacheStatistics stats = service.getCacheStatistics();

        // Then
        assertEquals("REDIS", stats.getCacheType());
        assertTrue(stats.isEnabled());
        assertEquals(2, stats.getTtlHours());
        assertEquals(-1, stats.getMaxSize()); // Redis doesn't have fixed max size
        assertEquals(2, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(3, stats.getRequestCount());
        assertEquals(2.0/3.0, stats.getHitRate(), 0.01);

        assertNotNull(stats.getDetailedStats());
        assertEquals("localhost", stats.getDetailedStats().get("redisHost"));
        assertEquals(6379, stats.getDetailedStats().get("redisPort"));
        assertEquals(0, stats.getDetailedStats().get("redisDatabase"));
        assertEquals("sharding:tenant:", stats.getDetailedStats().get("keyPrefix"));
    }

    @Test
    void testGetCacheStatisticsWithUnknownCacheType() {
        // Given
        org.springframework.cache.Cache unknownCache = new NoOpCache("test");

        when(cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE)).thenReturn(unknownCache);
        when(cacheConfig.isEnabled()).thenReturn(true);
        when(cacheConfig.getType()).thenReturn(ShardingConfigProperties.CacheType.CAFFEINE);
        when(cacheConfig.getTtlHours()).thenReturn(1);
        when(cacheConfig.getMaxSize()).thenReturn(1000);

        // When
        CacheStatisticsService.CacheStatistics stats = service.getCacheStatistics();

        // Then
        assertEquals("CAFFEINE", stats.getCacheType());
        assertTrue(stats.isEnabled());
        assertEquals(1, stats.getTtlHours());
        assertEquals(1000, stats.getMaxSize());
    }

    @Test
    void testRecordHitAndMiss() {
        // Given
        when(cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE)).thenReturn(null);

        // When
        service.recordHit();
        service.recordHit();
        service.recordMiss();

        CacheStatisticsService.CacheStatistics stats = service.getCacheStatistics();

        // Then
        assertEquals(2, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(3, stats.getRequestCount());
        assertEquals(2.0/3.0, stats.getHitRate(), 0.01);
    }

    @Test
    void testClearCache() {
        // Given
        org.springframework.cache.Cache cache = mock(org.springframework.cache.Cache.class);
        when(cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE)).thenReturn(cache);

        // Record some stats
        service.recordHit();
        service.recordMiss();

        // When
        service.clearCache();

        // Then
        verify(cache).clear();

        // Stats should be reset
        CacheStatisticsService.CacheStatistics stats = service.getCacheStatistics();
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(0, stats.getRequestCount());
    }

    @Test
    void testClearCacheWithNullCache() {
        // Given
        when(cacheManager.getCache(CacheConfiguration.TENANT_SHARD_CACHE)).thenReturn(null);

        // Record some stats
        service.recordHit();
        service.recordMiss();

        // When
        service.clearCache();

        // Then - should not throw exception, stats should be reset
        CacheStatisticsService.CacheStatistics stats = service.getCacheStatistics();
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(0, stats.getRequestCount());
    }

    @Test
    void testCacheStatisticsDataClass() {
        // Given
        CacheStatisticsService.CacheStatistics stats = new CacheStatisticsService.CacheStatistics();

        // When
        stats.setCacheType("TEST");
        stats.setEnabled(true);
        stats.setTtlHours(2);
        stats.setMaxSize(500);
        stats.setSize(100);
        stats.setHitCount(80);
        stats.setMissCount(20);
        stats.setRequestCount(100);
        stats.setHitRate(0.8);
        stats.setEvictionCount(5);
        stats.setAverageLoadTime(1.5);

        // Then
        assertEquals("TEST", stats.getCacheType());
        assertTrue(stats.isEnabled());
        assertEquals(2, stats.getTtlHours());
        assertEquals(500, stats.getMaxSize());
        assertEquals(100, stats.getSize());
        assertEquals(80, stats.getHitCount());
        assertEquals(20, stats.getMissCount());
        assertEquals(100, stats.getRequestCount());
        assertEquals(0.8, stats.getHitRate());
        assertEquals(5, stats.getEvictionCount());
        assertEquals(1.5, stats.getAverageLoadTime());
        assertNotNull(stats.getDetailedStats());
    }
}