# Sharding Spring Boot Starter

A comprehensive Spring Boot auto-configuration library for multi-tenant database sharding with replica support, query validation, and automated tenant management.

## Features

🚀 **Auto-Configuration** - Zero-configuration setup with Spring Boot
🏢 **Multi-Tenancy** - Complete tenant isolation across sharded databases
📊 **Directory-Based Sharding** - Tenant-to-shard mapping via lookup table
🔄 **Read/Write Splitting** - Master-replica configuration per shard
⚡ **Connection Pooling** - Optimized HikariCP configuration with database-specific tuning
🛡️ **Query Validation** - SQL-level tenant filtering validation with configurable strictness
🏷️ **Entity Classification** - `@ShardedEntity` annotation for automatic routing
📋 **Batch Processing** - Tenant iterator for background jobs with parallel support
🔍 **Monitoring** - JMX metrics and routing statistics
🛠️ **Developer Experience** - Comprehensive validation and clear error messages
🗄️ **Multi-Database Support** - Native support for MySQL and PostgreSQL with database-specific optimizations
⚡ **High-Performance Caching** - In-memory (Caffeine) and distributed (Redis) caching with 1-hour TTL

## Quick Start

### 1. Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>com.valarpirai</groupId>
    <artifactId>sharding-springboot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure Application Properties

**MySQL Configuration:**
```properties
# Global Database (for tenant_shard_mapping table)
app.sharding.global-db.url=jdbc:mysql://localhost:3306/global_db
app.sharding.global-db.username=global_user
app.sharding.global-db.password=global_password
app.sharding.global-db.hikari.maximum-pool-size=10

# Shard 1 - Latest shard for new signups
app.sharding.shard1.master.url=jdbc:mysql://localhost:3306/shard1_db
app.sharding.shard1.master.username=shard1_user
app.sharding.shard1.master.password=shard1_password

app.sharding.shard1.replica1.url=jdbc:mysql://localhost:3307/shard1_db
app.sharding.shard1.replica1.username=shard1_user
app.sharding.shard1.replica1.password=shard1_password

app.sharding.shard1.hikari.maximum-pool-size=20
app.sharding.shard1.hikari.minimum-idle=5
app.sharding.shard1.latest=true

# Shard 2
app.sharding.shard2.master.url=jdbc:mysql://localhost:3309/shard2_db
app.sharding.shard2.master.username=shard2_user
app.sharding.shard2.master.password=shard2_password
app.sharding.shard2.hikari.maximum-pool-size=15

# Tenant Configuration
app.sharding.tenant-column-names=tenant_id,company_id
app.sharding.validation.strictness=STRICT

# Caching Configuration (optional - improves performance)
app.sharding.cache.enabled=true
app.sharding.cache.type=CAFFEINE
app.sharding.cache.ttl-hours=1
```

**PostgreSQL Configuration:**
```properties
# Global Database (for tenant_shard_mapping table)
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=global_user
app.sharding.global-db.password=global_password
app.sharding.global-db.hikari.maximum-pool-size=10

# Shard 1 - Latest shard for new signups
app.sharding.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shard1.master.username=shard1_user
app.sharding.shard1.master.password=shard1_password

app.sharding.shard1.replica1.url=jdbc:postgresql://localhost:5433/shard1_db
app.sharding.shard1.replica1.username=shard1_user
app.sharding.shard1.replica1.password=shard1_password

# Database-specific optimizations applied automatically
app.sharding.shard1.hikari.maximum-pool-size=20
app.sharding.shard1.hikari.minimum-idle=5
app.sharding.shard1.latest=true

# Tenant Configuration
app.sharding.tenant-column-names=tenant_id,company_id
app.sharding.validation.strictness=STRICT

# Caching Configuration (optional - improves performance)
app.sharding.cache.enabled=true
app.sharding.cache.type=REDIS
app.sharding.cache.ttl-hours=1
app.sharding.cache.redis-host=localhost
app.sharding.cache.redis-port=6379
```

### 3. Annotate Your Entities

```java
@Entity
@ShardedEntity  // This entity will be sharded
public class Customer {
    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false)  // Required for sharded entities
    private Long tenantId;

    private String name;
    private String email;

    // ... getters and setters
}

@Entity
// No annotation - stored in global database
public class GlobalConfig {
    @Id
    private Long id;

    private String configKey;
    private String configValue;

    // ... getters and setters
}
```

### 4. Use in Your Application

```java
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Customer createCustomer(Customer customer) {
        // Set tenant context before database operations
        TenantContext.setTenantId(customer.getTenantId());
        try {
            return customerRepository.save(customer);
        } finally {
            TenantContext.clear();
        }
    }

    public List<Customer> getCustomersByTenant(Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            return customerRepository.findAll(); // Automatically routed to correct shard
        });
    }
}
```

## Core Concepts

### Directory-Based Sharding

The library uses a `tenant_shard_mapping` table in a global database to determine which shard contains each tenant's data. The table is automatically created with database-specific optimizations:

**MySQL:**
```sql
CREATE TABLE tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**PostgreSQL:**
```sql
CREATE TABLE tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);
```

Both versions include optimized indexes for performance.

### Entity Classification

- **Sharded Entities**: Annotated with `@ShardedEntity`, must include tenant column, stored in tenant-specific shards
- **Global Entities**: No annotation, stored in global database, shared across tenants

### Tenant Context Management

```java
// Manual context management
TenantContext.setTenantId(1001L);
// ... database operations ...
TenantContext.clear();

// Automatic context management
TenantContext.executeInTenantContext(1001L, () -> {
    return customerService.findAll();
});

// Enable read-only mode for replica routing
TenantContext.setReadOnlyMode(true);
```

## Configuration Reference

### Global Database Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `app.sharding.global-db.url` | JDBC URL for global database | *Required* |
| `app.sharding.global-db.username` | Global database username | *Required* |
| `app.sharding.global-db.password` | Global database password | *Required* |
| `app.sharding.global-db.hikari.*` | HikariCP settings for global pool | See HikariCP section |

### Shard Configuration

```properties
# Master database (required)
app.sharding.{shardId}.master.url=jdbc:mysql://...
app.sharding.{shardId}.master.username=user
app.sharding.{shardId}.master.password=password

# Replica databases (optional)
app.sharding.{shardId}.replica1.url=jdbc:mysql://...
app.sharding.{shardId}.replica2.url=jdbc:mysql://...

# Shard metadata
app.sharding.{shardId}.latest=true           # Mark as latest for new tenants
app.sharding.{shardId}.region=us-east-1      # Region identifier
app.sharding.{shardId}.status=ACTIVE         # Shard status

# HikariCP settings per shard
app.sharding.{shardId}.hikari.maximum-pool-size=20
app.sharding.{shardId}.hikari.minimum-idle=5
app.sharding.{shardId}.hikari.connection-timeout=30s
```

### HikariCP Configuration

The library applies optimal defaults and database-specific optimizations:

| Property | Default | Description |
|----------|---------|-------------|
| `maximum-pool-size` | 20 | Maximum connections in pool |
| `minimum-idle` | 5 | Minimum idle connections |
| `connection-timeout` | 30s | Time to wait for connection |
| `idle-timeout` | 10m | Idle connection timeout |
| `max-lifetime` | 30m | Maximum connection lifetime |
| `validation-timeout` | 5s | Connection validation timeout |
| `register-mbeans` | true | Enable JMX monitoring |

**Database-Specific Optimizations:**
- **MySQL**: Prepared statement caching, server-side preparation, UTF8MB4 charset
- **PostgreSQL**: Prepared statement optimization, statement cache tuning
- **SQL Server**: Statement pooling configuration *(coming soon)*
- **Oracle**: Implicit statement caching *(coming soon)*

**Supported Databases:**
- ✅ **MySQL 5.7+** - Full support with optimized connection pooling
- ✅ **PostgreSQL 11+** - Full support with PostgreSQL-specific optimizations
- 🔄 **SQL Server** - Planned for future release
- 🔄 **Oracle** - Planned for future release

### Validation Configuration

| Property | Values | Description |
|----------|--------|-------------|
| `app.sharding.validation.strictness` | `STRICT`, `WARN`, `LOG`, `DISABLED` | Query validation strictness |
| `app.sharding.tenant-column-names` | List of strings | Valid tenant column names |

**Validation Strictness Levels:**
- **STRICT**: Throw exception if tenant column missing *(recommended for production)*
- **WARN**: Log warning but allow query to proceed
- **LOG**: Log info message about validation
- **DISABLED**: No validation performed

### Cache Configuration

| Property | Values | Description |
|----------|--------|-------------|
| `app.sharding.cache.enabled` | `true`, `false` | Enable/disable tenant-shard mapping cache |
| `app.sharding.cache.type` | `CAFFEINE`, `REDIS` | Cache implementation type |
| `app.sharding.cache.ttl-hours` | Integer | Cache entry time-to-live in hours |
| `app.sharding.cache.max-size` | Integer | Maximum cache size (Caffeine only) |
| `app.sharding.cache.record-stats` | `true`, `false` | Enable cache statistics collection |

**Redis-Specific Configuration:**

| Property | Default | Description |
|----------|---------|-------------|
| `app.sharding.cache.redis-host` | `localhost` | Redis server hostname |
| `app.sharding.cache.redis-port` | `6379` | Redis server port |
| `app.sharding.cache.redis-database` | `0` | Redis database number |
| `app.sharding.cache.redis-password` | *(none)* | Redis authentication password |
| `app.sharding.cache.redis-key-prefix` | `sharding:tenant:` | Key prefix for cache entries |
| `app.sharding.cache.redis-connection-timeout-ms` | `2000` | Connection timeout in milliseconds |

**Cache Performance Benefits:**
- ⚡ **Reduced Latency**: Eliminates database lookup on every query
- 📈 **Higher Throughput**: Supports thousands of requests per second
- 🔄 **Automatic Management**: Cache warming, eviction, and refresh
- 📊 **Observable**: Built-in metrics and health monitoring
- 🌐 **Scalable**: Redis support for distributed applications

## Advanced Features

### Background Job Processing

```java
@Component
public class TenantReportJob {

    private final TenantIterator tenantIterator;

    public void generateReportsForAllTenants() {
        // Process tenants in batches of 10
        tenantIterator.processAllTenants(this::generateReport, 10);
    }

    public CompletableFuture<Void> generateReportsAsync() {
        // Parallel processing across tenants
        return tenantIterator.processAllTenantsAsync(this::generateReport);
    }

    private void generateReport(Long tenantId) {
        // This runs with tenant context automatically set
        log.info("Generating report for tenant: {}", tenantId);
        // ... report generation logic ...
    }
}
```

### Tenant Management

```java
@Service
public class TenantOnboardingService {

    private final ShardUtils shardUtils;

    public void onboardNewTenant(Long tenantId) {
        // Assign to latest shard automatically
        TenantShardMapping mapping = shardUtils.assignTenantToLatestShard(tenantId);
        log.info("Assigned tenant {} to shard: {}", tenantId, mapping.getShardId());
    }

    public void migrateTenant(Long tenantId, String targetShardId) {
        // Move tenant to different shard
        boolean success = shardUtils.moveTenantToShard(tenantId, targetShardId);
        if (success) {
            log.info("Migrated tenant {} to shard: {}", tenantId, targetShardId);
        }
    }

    public ShardUtils.ShardStatistics getShardStats() {
        return shardUtils.getShardStatistics();
    }
}
```

### Monitoring and Observability

```java
@RestController
@RequestMapping("/admin/sharding")
public class ShardingAdminController {

    private final ConnectionRouter connectionRouter;
    private final ShardUtils shardUtils;

    @GetMapping("/stats")
    public ResponseEntity<Object> getShardingStatistics() {
        return ResponseEntity.ok(Map.of(
            "routing", connectionRouter.getRoutingStatistics(),
            "shards", shardUtils.getShardStatistics(),
            "tenants", shardUtils.getTenantDistribution()
        ));
    }

    @GetMapping("/health/{shardId}")
    public ResponseEntity<String> checkShardHealth(@PathVariable String shardId) {
        boolean available = connectionRouter.isShardAvailable(shardId);
        return ResponseEntity.ok(available ? "UP" : "DOWN");
    }
}
```

## Database Setup

The library automatically creates the required `tenant_shard_mapping` table with database-specific optimizations. For manual setup or reference, here are the schemas:

### 1. Global Database Schema

**MySQL:**
```sql
-- Create global database
CREATE DATABASE global_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Tenant to shard mapping (automatically created by library)
CREATE TABLE tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id),
    INDEX idx_shard_id (shard_id),
    INDEX idx_shard_status (shard_status),
    INDEX idx_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Global configuration (example non-sharded table)
CREATE TABLE global_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**PostgreSQL:**
```sql
-- Create global database
CREATE DATABASE global_db;

-- Tenant to shard mapping (automatically created by library)
CREATE TABLE tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
);

-- Indexes
CREATE INDEX idx_shard_id ON tenant_shard_mapping (shard_id);
CREATE INDEX idx_shard_status ON tenant_shard_mapping (shard_status);
CREATE INDEX idx_region ON tenant_shard_mapping (region);

-- Global configuration (example non-sharded table)
CREATE TABLE global_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2. Shard Database Schema

```sql
-- Create for each shard (shard1_db, shard2_db, etc.)
CREATE DATABASE shard1_db;

-- Sharded entities (must include tenant_id)
CREATE TABLE customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    UNIQUE KEY unique_tenant_email (tenant_id, email)
);
```

### 3. Sample Data

```sql
-- Tenant assignments
INSERT INTO global_db.tenant_shard_mapping (tenant_id, shard_id, region, shard_status) VALUES
(1001, 'shard1', 'us-east-1', 'ACTIVE'),
(1002, 'shard1', 'us-east-1', 'ACTIVE'),
(1003, 'shard2', 'us-west-2', 'ACTIVE');

-- Global configuration
INSERT INTO global_db.global_config (config_key, config_value, description) VALUES
('max_customers_per_tenant', '1000', 'Maximum customers per tenant'),
('default_region', 'us-east-1', 'Default region for new tenants');
```

## Error Handling and Troubleshooting

### Common Issues

**1. Entity Validation Errors**
```
Entity validation failed for Customer: Sharded entity must have one of the configured tenant columns: [tenant_id, company_id]
```
*Solution*: Add a tenant column to your `@ShardedEntity` annotated class.

**2. Missing Tenant Context**
```
Tenant context is required for sharded entity operations
```
*Solution*: Set tenant context before database operations:
```java
TenantContext.setTenantId(tenantId);
```

**3. No Shard Mapping Found**
```
No shard mapping found for tenant: 1001
```
*Solution*: Create tenant-shard mapping:
```java
shardUtils.assignTenantToLatestShard(1001L);
```

**4. Query Validation Failures**
```
Sharded entity query validation failed: SELECT query missing tenant filtering in WHERE clause
```
*Solution*: Include tenant filtering in your queries or adjust validation strictness.

### Validation Configuration

For development environments, you might want to use less strict validation:

```properties
# Development settings
app.sharding.validation.strictness=WARN

# Production settings (recommended)
app.sharding.validation.strictness=STRICT
```

### Logging Configuration

```properties
# Enable debug logging for sharding operations
logging.level.com.valarpirai.sharding=DEBUG

# Monitor connection routing
logging.level.com.valarpirai.sharding.routing=INFO

# Track tenant context operations
logging.level.com.valarpirai.sharding.context=DEBUG
```

## Performance Tuning

### Connection Pool Optimization

```properties
# High-traffic shard
app.sharding.shard1.hikari.maximum-pool-size=50
app.sharding.shard1.hikari.minimum-idle=10
app.sharding.shard1.hikari.connection-timeout=20s

# Read-heavy workload with replicas
app.sharding.shard1.hikari.maximum-pool-size=30  # Master
app.sharding.shard1.replica1.hikari.maximum-pool-size=40  # Replica
```

### Caching for High Performance

The library includes comprehensive caching to reduce database lookups and improve performance:

**Caffeine (In-Memory) Cache:**
```properties
# High-performance in-memory caching
app.sharding.cache.enabled=true
app.sharding.cache.type=CAFFEINE
app.sharding.cache.ttl-hours=1
app.sharding.cache.max-size=10000
app.sharding.cache.record-stats=true
```

**Redis (Distributed) Cache:**
```properties
# Distributed cache for multiple application instances
app.sharding.cache.enabled=true
app.sharding.cache.type=REDIS
app.sharding.cache.ttl-hours=1
app.sharding.cache.redis-host=localhost
app.sharding.cache.redis-port=6379
app.sharding.cache.redis-database=0
app.sharding.cache.redis-key-prefix=sharding:tenant:
app.sharding.cache.redis-password=your-redis-password
app.sharding.cache.record-stats=true
```

**Cache Management:**
```java
@RestController
public class CacheController {

    private final CacheStatisticsService cacheStatisticsService;
    private final ShardLookupService shardLookupService;

    // Get cache performance metrics
    @GetMapping("/cache/stats")
    public CacheStatistics getCacheStats() {
        return cacheStatisticsService.getCacheStatistics();
    }

    // Clear all cached mappings
    @PostMapping("/cache/clear")
    public void clearCache() {
        shardLookupService.clearCache();
    }

    // Evict specific tenant
    @DeleteMapping("/cache/tenant/{tenantId}")
    public void evictTenant(@PathVariable Long tenantId) {
        shardLookupService.evictFromCache(tenantId);
    }

    // Pre-load frequently used tenants
    @PostMapping("/cache/warmup")
    public void warmUpCache(@RequestBody List<Long> tenantIds) {
        shardLookupService.warmUpCache(tenantIds);
    }
}
```

### Database-Specific Tuning

The library automatically detects your database type and applies optimal configurations:

**MySQL Optimizations (Applied Automatically):**
```properties
# Connection properties added automatically for MySQL
cachePrepStmts=true
prepStmtCacheSize=250
prepStmtCacheSqlLimit=2048
useServerPrepStmts=true
```

**PostgreSQL Optimizations (Applied Automatically):**
```properties
# Connection properties added automatically for PostgreSQL
prepareThreshold=1
preparedStatementCacheQueries=256
preparedStatementCacheSizeMiB=5
```

**Manual Database-Specific Configuration (Optional):**
```properties
# Override automatic detection if needed
app.sharding.shard1.master.driver-class-name=com.mysql.cj.jdbc.Driver
app.sharding.shard2.master.driver-class-name=org.postgresql.Driver

# Mix MySQL and PostgreSQL in the same application
app.sharding.shard1.master.url=jdbc:mysql://localhost:3306/shard1_db
app.sharding.shard2.master.url=jdbc:postgresql://localhost:5432/shard2_db
```

### Read/Write Splitting

```java
@Service
public class CustomerService {

    // Read operations - use replicas
    public List<Customer> findCustomers(Long tenantId) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            TenantContext.setReadOnlyMode(true);
            return customerRepository.findAll();
        });
    }

    // Write operations - use master
    public Customer saveCustomer(Customer customer) {
        return TenantContext.executeInTenantContext(customer.getTenantId(), () -> {
            // Read-only mode is false by default
            return customerRepository.save(customer);
        });
    }
}
```

## Migration Guide

### From Single Database

1. **Create Shard Infrastructure**
   - Set up global database and shard databases
   - Configure application properties

2. **Annotate Entities**
   - Add `@ShardedEntity` to tenant-specific entities
   - Ensure tenant columns exist and are properly annotated

3. **Update Application Code**
   - Add tenant context management
   - Update service layer to set tenant context

4. **Data Migration**
   - Export tenant data from single database
   - Create tenant-shard mappings
   - Import data into appropriate shards

### From Existing Multi-Tenant System

1. **Configure Sharding Library**
   - Map existing databases to shard configuration
   - Create tenant_shard_mapping entries
   - Library automatically detects MySQL or PostgreSQL

2. **Database Migration** (if switching database types)
   - Use database-specific setup scripts provided
   - Library handles SQL dialect differences automatically
   - No application code changes needed

3. **Gradual Migration**
   - Start with validation in `WARN` mode
   - Gradually move to `STRICT` mode
   - Update queries to include proper tenant filtering

## Best Practices

### Security

✅ **Never expose tenant context in URLs or public APIs**
✅ **Validate tenant access in authentication/authorization layer**
✅ **Use connection pooling with proper credentials per shard**
✅ **Enable query validation in production (`STRICT` mode)**
✅ **Monitor and log all tenant context changes**

### Performance

✅ **Use read replicas for read-heavy workloads**
✅ **Configure appropriate connection pool sizes per shard**
✅ **Enable JMX monitoring for connection pools**
✅ **Use batch processing for cross-tenant operations**
✅ **Cache tenant-shard mappings when appropriate**
✅ **Let the library auto-detect database types for optimal performance**
✅ **Use database-specific setup scripts for consistent schema creation**

### Operational

✅ **Monitor shard utilization and tenant distribution**
✅ **Plan shard capacity based on tenant growth**
✅ **Use the latest shard feature for balanced growth**
✅ **Implement proper backup strategies per shard**
✅ **Test tenant migration procedures**
✅ **Consider database type when planning capacity (MySQL vs PostgreSQL performance characteristics)**
✅ **Use provided database setup scripts for consistent environments**

## Sample Application

A complete sample application demonstrating the sharding library is included in the `sample-sharded-app/` directory.

### Running with MySQL
```bash
# Setup MySQL databases
mysql < sample-sharded-app/database-setup.sql

# Run the application
cd sample-sharded-app
mvn spring-boot:run
```

### Running with PostgreSQL
```bash
# Setup PostgreSQL databases
psql -f sample-sharded-app/database-setup-postgresql.sql

# Run with PostgreSQL profile
cd sample-sharded-app
mvn spring-boot:run -Dspring-boot.run.profiles=postgresql
```

### Sample API Usage
```bash
# Set tenant context
POST localhost:8080/api/customers/set-tenant/1001

# Create customer (routes to correct shard automatically)
POST localhost:8080/api/customers
{
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0101"
}

# Get customers for current tenant
GET localhost:8080/api/customers

# Cache management endpoints
GET localhost:8080/api/cache/stats      # View cache statistics
POST localhost:8080/api/cache/clear     # Clear all cache entries
POST localhost:8080/api/cache/warmup/sample  # Warm up cache with sample data
DELETE localhost:8080/api/cache/tenant/1001  # Evict specific tenant
```

The sample application demonstrates:
- ✅ **Database Detection** - Automatic MySQL/PostgreSQL detection
- ✅ **Entity Routing** - `@ShardedEntity` vs global entities
- ✅ **Tenant Context** - Automatic shard routing based on tenant
- ✅ **Query Validation** - SQL validation with configurable strictness
- ✅ **Read/Write Splitting** - Master/replica routing
- ✅ **High-Performance Caching** - Caffeine and Redis cache implementations
- ✅ **Cache Management** - Statistics, warming, and eviction APIs
- ✅ **Configuration** - Complete setup examples for both databases

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please read our contributing guidelines and submit pull requests to our repository.

## Support

- 📖 **Documentation**: [Full API Documentation](docs/)
- 🐛 **Issues**: [GitHub Issues](https://github.com/valarpirai/galaxy-sharding/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/valarpirai/galaxy-sharding/discussions)
- 📧 **Email**: support@valarpirai.com