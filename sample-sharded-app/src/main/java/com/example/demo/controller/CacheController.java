package com.example.demo.controller;

import com.valarpirai.sharding.cache.CacheStatisticsService;
import com.valarpirai.sharding.lookup.ShardLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST controller for cache management and monitoring operations.
 * Provides endpoints to view cache statistics and manage cache contents.
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);

    private final CacheStatisticsService cacheStatisticsService;
    private final ShardLookupService shardLookupService;

    public CacheController(CacheStatisticsService cacheStatisticsService,
                          ShardLookupService shardLookupService) {
        this.cacheStatisticsService = cacheStatisticsService;
        this.shardLookupService = shardLookupService;
    }

    /**
     * Get comprehensive cache statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<CacheStatisticsService.CacheStatistics> getCacheStatistics() {
        try {
            CacheStatisticsService.CacheStatistics stats = cacheStatisticsService.getCacheStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting cache statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear all cache entries.
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearCache() {
        try {
            shardLookupService.clearCache();
            return ResponseEntity.ok("Cache cleared successfully");
        } catch (Exception e) {
            logger.error("Error clearing cache", e);
            return ResponseEntity.internalServerError().body("Failed to clear cache: " + e.getMessage());
        }
    }

    /**
     * Evict specific tenant from cache.
     */
    @DeleteMapping("/tenant/{tenantId}")
    public ResponseEntity<String> evictTenant(@PathVariable Long tenantId) {
        try {
            shardLookupService.evictFromCache(tenantId);
            return ResponseEntity.ok("Tenant " + tenantId + " evicted from cache");
        } catch (Exception e) {
            logger.error("Error evicting tenant {} from cache", tenantId, e);
            return ResponseEntity.internalServerError().body("Failed to evict tenant: " + e.getMessage());
        }
    }

    /**
     * Warm up cache with specific tenants.
     */
    @PostMapping("/warmup")
    public ResponseEntity<String> warmUpCache(@RequestBody List<Long> tenantIds) {
        try {
            if (tenantIds == null || tenantIds.isEmpty()) {
                return ResponseEntity.badRequest().body("Tenant IDs list cannot be empty");
            }

            shardLookupService.warmUpCache(tenantIds);
            return ResponseEntity.ok("Cache warmed up with " + tenantIds.size() + " tenants");
        } catch (Exception e) {
            logger.error("Error warming up cache", e);
            return ResponseEntity.internalServerError().body("Failed to warm up cache: " + e.getMessage());
        }
    }

    /**
     * Warm up cache with sample tenants for demo purposes.
     */
    @PostMapping("/warmup/sample")
    public ResponseEntity<String> warmUpCacheWithSampleData() {
        try {
            // Sample tenant IDs from the database setup
            List<Long> sampleTenants = Arrays.asList(1001L, 1002L, 1003L, 2001L, 2002L, 3001L, 3002L);
            shardLookupService.warmUpCache(sampleTenants);
            return ResponseEntity.ok("Cache warmed up with " + sampleTenants.size() + " sample tenants");
        } catch (Exception e) {
            logger.error("Error warming up cache with sample data", e);
            return ResponseEntity.internalServerError().body("Failed to warm up cache: " + e.getMessage());
        }
    }

    /**
     * Get cache health summary.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getCacheHealth() {
        try {
            CacheStatisticsService.CacheStatistics stats = cacheStatisticsService.getCacheStatistics();

            Map<String, Object> health = Map.of(
                "enabled", stats.isEnabled(),
                "cacheType", stats.getCacheType(),
                "hitRate", stats.getHitRate(),
                "totalRequests", stats.getRequestCount(),
                "cacheSize", stats.getSize(),
                "status", stats.isEnabled() ? "UP" : "DOWN"
            );

            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error("Error getting cache health", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}