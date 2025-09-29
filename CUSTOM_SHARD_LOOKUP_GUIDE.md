# Custom ITenantShardMappingRepo Implementation Guide

## 🎯 **Overview**

The Galaxy Sharding library allows you to provide your own custom implementation of the `ITenantShardMappingRepo` interface to override the default database-backed shard lookup behavior. This enables you to integrate with external systems, implement custom sharding algorithms, or use different data stores for tenant-to-shard mappings.

## 📋 **Architecture**

### **Default Implementation**
- **TenantShardMappingRepository**: Database-backed implementation using global database
- **Caching**: Built-in caching using Caffeine/Redis
- **Database Agnostic**: Supports PostgreSQL, MySQL, SQL Server

### **Custom Implementation**
- **ITenantShardMappingRepo**: Interface that you implement
- **Auto-Configuration**: Automatically detects and uses custom implementations
- **Spring Integration**: Full integration with dependency injection

## 🔧 **How to Provide Custom Implementation**

### **Step 1: Create Your Implementation**

```java
@Service
@Primary  // Optional: Use if you have multiple implementations
public class MyCustomShardLookupService implements ITenantShardMappingRepo {

    private static final Logger logger = LoggerFactory.getLogger(MyCustomShardLookupService.class);

    @Override
    public Optional<TenantShardMapping> findShardByTenantId(Long tenantId) {
        // Your custom logic here
        // Example: External API call, configuration file lookup, etc.

        if (tenantId == null) {
            return Optional.empty();
        }

        // Example: Simple modulo-based sharding
        String shardId = "shard" + (tenantId % 3 + 1); // Routes to shard1, shard2, or shard3
        TenantShardMapping mapping = new TenantShardMapping(tenantId, shardId, "us-east-1", "ACTIVE");

        logger.info("Custom shard lookup: tenant {} -> shard {}", tenantId, shardId);
        return Optional.of(mapping);
    }

    @Override
    public String getLatestShardId() {
        // Return the shard for new tenants
        return "shard1"; // or implement your logic
    }

    @Override
    public TenantShardMapping createMapping(Long tenantId, String shardId, String region) {
        // Implement tenant onboarding logic
        logger.info("Creating mapping: tenant={}, shard={}", tenantId, shardId);
        return new TenantShardMapping(tenantId, shardId, region, "ACTIVE");
    }

    @Override
    public boolean updateMapping(Long tenantId, String newShardId, String newRegion, String newStatus) {
        // Implement shard migration logic
        logger.info("Updating mapping: tenant={} -> shard={}", tenantId, newShardId);
        return true;
    }

    @Override
    public List<TenantShardMapping> findAllMappings() {
        // Return all tenant mappings (for admin purposes)
        return List.of(); // Implement based on your data source
    }

    // Optional: Implement caching methods if your service supports caching
    @Override
    public void evictFromCache(Long tenantId) {
        // Custom cache eviction logic
    }

    @Override
    public void clearCache() {
        // Custom cache clearing logic
    }

    @Override
    public void warmUpCache(List<Long> tenantIds) {
        // Custom cache warming logic
    }
}
```

### **Step 2: Register Your Implementation**

The auto-configuration will automatically detect your custom implementation:

```java
@Configuration
public class MyShardingConfiguration {

    // Your custom implementation will be automatically used
    // No additional configuration needed!

    // Optional: If you need to inject dependencies
    @Bean
    @Primary
    public ITenantShardMappingRepo customShardLookupService(
            ExternalShardingApiClient apiClient,
            CacheManager cacheManager) {
        return new MyCustomShardLookupService(apiClient, cacheManager);
    }
}
```

### **Step 3: Disable Default Implementation (Optional)**

If you want to completely disable the default database-backed implementation:

```properties
# Disable default TenantShardMappingRepository bean creation
spring.autoconfigure.exclude=com.valarpirai.sharding.config.ShardingAutoConfiguration
```

Or use conditional configuration:

```java
@ConditionalOnProperty(name = "app.sharding.custom-lookup.enabled", havingValue = "true")
@Service
public class MyCustomShardLookupService implements ITenantShardMappingRepo {
    // Implementation
}
```

## 🚀 **Usage Examples**

### **External API-Based Lookup**

```java
@Service
public class ApiBasedShardLookupService implements ITenantShardMappingRepo {

    private final WebClient shardingApiClient;
    private final CacheManager cacheManager;

    public ApiBasedShardLookupService(WebClient.Builder webClientBuilder) {
        this.shardingApiClient = webClientBuilder
            .baseUrl("https://sharding-api.company.com")
            .build();
        this.cacheManager = null; // or inject if needed
    }

    @Override
    public Optional<TenantShardMapping> findShardByTenantId(Long tenantId) {
        try {
            // Call external sharding API
            ShardApiResponse response = shardingApiClient
                .get()
                .uri("/tenants/{tenantId}/shard", tenantId)
                .retrieve()
                .bodyToMono(ShardApiResponse.class)
                .block();

            if (response != null && response.getShardId() != null) {
                return Optional.of(new TenantShardMapping(
                    tenantId,
                    response.getShardId(),
                    response.getRegion(),
                    response.getStatus()
                ));
            }
        } catch (Exception e) {
            logger.error("Failed to lookup shard for tenant {}: {}", tenantId, e.getMessage());
        }

        return Optional.empty();
    }

    // Implement other methods...
}
```

### **Configuration-Based Lookup**

```java
@Service
public class ConfigBasedShardLookupService implements ITenantShardMappingRepo {

    private final Map<Long, String> tenantToShardMapping;

    public ConfigBasedShardLookupService(@Value("${app.tenant-shard-mappings}") String mappingsConfig) {
        // Parse configuration file or properties
        this.tenantToShardMapping = parseMappings(mappingsConfig);
    }

    @Override
    public Optional<TenantShardMapping> findShardByTenantId(Long tenantId) {
        String shardId = tenantToShardMapping.get(tenantId);
        if (shardId != null) {
            return Optional.of(new TenantShardMapping(tenantId, shardId, "us-east-1", "ACTIVE"));
        }
        return Optional.empty();
    }

    // Implement other methods...
}
```

### **Hybrid Approach (Database + External)**

```java
@Service
public class HybridShardLookupService implements ITenantShardMappingRepo {

    private final TenantShardMappingRepository defaultService;
    private final ExternalShardingService externalService;

    @Override
    public Optional<TenantShardMapping> findShardByTenantId(Long tenantId) {
        // Try external service first
        Optional<TenantShardMapping> external = externalService.findShard(tenantId);
        if (external.isPresent()) {
            return external;
        }

        // Fallback to database
        return defaultService.findShardByTenantId(tenantId);
    }

    // Implement other methods...
}
```

## ⚙️ **Integration Points**

### **Components That Use ITenantShardMappingRepo**

All these components automatically receive your custom implementation:

- **ShardAwareDataSourceDelegate**: Routes database connections
- **ShardUtils**: Utility methods for shard operations
- **TenantIterator**: Batch processing across tenants
- **ShardSelectorFilter**: HTTP request shard resolution

### **Caching Integration**

Your custom implementation can leverage Spring's caching:

```java
@Service
public class CachedCustomShardLookupService implements ITenantShardMappingRepo {

    @Override
    @Cacheable(value = "tenantShardMappings", key = "#tenantId")
    public Optional<TenantShardMapping> findShardByTenantId(Long tenantId) {
        // Your expensive lookup logic
        return performExpensiveLookup(tenantId);
    }

    @Override
    @CacheEvict(value = "tenantShardMappings", key = "#tenantId")
    public void evictFromCache(Long tenantId) {
        // Cache eviction handled by annotation
    }

    @Override
    @CacheEvict(value = "tenantShardMappings", allEntries = true)
    public void clearCache() {
        // Clear all cache entries
    }
}
```

## 🔧 **Configuration Properties**

You can still use sharding configuration properties in your custom implementation:

```java
@Service
public class MyCustomShardLookupService implements ITenantShardMappingRepo {

    private final ShardingConfigProperties shardingConfig;

    public MyCustomShardLookupService(ShardingConfigProperties shardingConfig) {
        this.shardingConfig = shardingConfig;
    }

    @Override
    public String getLatestShardId() {
        // Use configuration to determine latest shard
        return shardingConfig.getShards().entrySet().stream()
            .filter(entry -> Boolean.TRUE.equals(entry.getValue().getLatest()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("shard1");
    }
}
```

## 🚨 **Important Considerations**

### **Performance**
- Ensure your custom implementation is performant
- Consider implementing caching if external calls are involved
- Use async operations for non-blocking lookups where possible

### **Error Handling**
- Always handle exceptions gracefully
- Provide meaningful fallback behavior
- Log errors appropriately for troubleshooting

### **Thread Safety**
- Ensure your implementation is thread-safe
- Use concurrent data structures if maintaining state
- Consider Spring's singleton scope implications

### **Testing**
```java
@ExtendWith(MockitoExtension.class)
class MyCustomShardLookupServiceTest {

    @Mock
    private ExternalShardingService externalService;

    @InjectMocks
    private MyCustomShardLookupService tenantShardMappingRepo;

    @Test
    void shouldReturnShardMapping() {
        // Test your custom implementation
        Optional<TenantShardMapping> result = tenantShardMappingRepo.findShardByTenantId(123L);
        assertThat(result).isPresent();
        assertThat(result.get().getShardId()).isEqualTo("shard1");
    }
}
```

## 📊 **Monitoring and Observability**

Your custom implementation can integrate with the observability features:

```java
@Service
public class ObservableCustomShardLookupService implements ITenantShardMappingRepo {

    @Override
    @WithSpan("custom.shard_lookup.find_by_tenant_id")
    public Optional<TenantShardMapping> findShardByTenantId(Long tenantId) {
        Span currentSpan = Span.current();
        currentSpan.setAttribute("tenant.id", tenantId.toString());
        currentSpan.setAttribute("lookup.type", "custom");

        // Your lookup logic
        Optional<TenantShardMapping> result = performLookup(tenantId);

        currentSpan.setAttribute("lookup.found", result.isPresent());
        if (result.isPresent()) {
            currentSpan.setAttribute("shard.id", result.get().getShardId());
        }

        return result;
    }
}
```

By implementing the `ITenantShardMappingRepo` interface, you have complete control over how tenant-to-shard mappings are resolved while maintaining full integration with the Galaxy Sharding library's features and Spring Boot ecosystem! 🚀