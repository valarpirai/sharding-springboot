# Java Backend Development Project Overview

This repository contains a comprehensive multi-tenant database sharding solution with Spring Boot auto-configuration.

## Project Structure

```
java-backend-dev/
├── sharding-springboot-starter/              # 🆕 NEW: Advanced sharding library (Java + Lombok)
├── sample-sharded-app/                       # 🆕 NEW: Demo application
└── PROJECT_OVERVIEW.md                       # This file
```

## 🆕 Sharding Spring Boot Starter

**Location**: `sharding-springboot-starter/`
**Language**: Java with Lombok
**Purpose**: Production-ready multi-tenant database sharding with comprehensive features

### Key Features Implemented

✅ **Directory-Based Sharding** - Tenant-to-shard mapping via `tenant_shard_mapping` table
✅ **Multi-Replica Support** - Master-replica configuration with automatic read/write splitting
✅ **Auto-Configuration** - Zero-config Spring Boot integration
✅ **Entity Validation** - `@ShardedEntity` annotation validation during startup
✅ **Query Validation** - SQL-level tenant filtering with configurable strictness
✅ **Connection Routing** - Intelligent routing based on tenant context
✅ **HikariCP Optimization** - Database-specific tuning with optimal defaults
✅ **Batch Processing** - Tenant iterator for background jobs
✅ **Monitoring** - JMX metrics and routing statistics
✅ **Multi-Database Support** - Native MySQL and PostgreSQL support with database-specific optimizations

### Architecture Components

#### 📁 Configuration (`/config/`)
- `ShardingConfigProperties` - Main configuration with flat structure
- `HikariConfigProperties` - Comprehensive HikariCP settings
- `ShardingAutoConfiguration` - Spring Boot auto-configuration
- `HikariConfigUtil` - Optimal defaults and database-specific optimizations

#### 🎯 Context Management (`/context/`)
- `TenantContext` - Thread-local tenant information
- `TenantInfo` - Immutable tenant data holder

#### 🔍 Shard Lookup (`/lookup/`)
- `ShardLookupService` - Directory-based tenant-shard mapping
- `TenantShardMapping` - Data model for lookup table
- `ShardUtils` - Comprehensive shard management utilities
- `DatabaseSqlProvider` - Interface for database-specific SQL operations
- `MySQLSqlProvider` - MySQL-specific SQL implementation
- `PostgreSQLSqlProvider` - PostgreSQL-specific SQL implementation
- `DatabaseSqlProviderFactory` - Automatic database detection and provider selection

#### 🔄 Connection Routing (`/routing/`)
- `ConnectionRouter` - Routes connections based on tenant context
- `RoutingDataSource` - Spring DataSource integration
- `ShardDataSources` - Master-replica container with load balancing

#### 🛡️ Validation (`/validation/`)
- `QueryValidator` - SQL query validation for tenant filtering
- `EntityValidator` - `@ShardedEntity` annotation validation
- `ValidatingDataSource` - DataSource proxy for query interception

#### 🔄 Batch Processing (`/iterator/`)
- `TenantIterator` - Batch processing with parallel support
- Background job utilities for cross-tenant operations

#### 🏷️ Annotations (`/annotation/`)
- `@ShardedEntity` - Marker for sharded entities

### Configuration Example

**MySQL Configuration:**
```properties
# Global Database
app.sharding.global-db.url=jdbc:mysql://localhost:3306/global_db
app.sharding.global-db.username=global_user
app.sharding.global-db.password=global_password

# Shard Configuration
app.sharding.shard1.master.url=jdbc:mysql://localhost:3306/shard1_db
app.sharding.shard1.replica1.url=jdbc:mysql://localhost:3307/shard1_db
app.sharding.shard1.hikari.maximum-pool-size=20
app.sharding.shard1.latest=true

# Validation
app.sharding.validation.strictness=STRICT
app.sharding.tenant-column-names=tenant_id,company_id
```

**PostgreSQL Configuration:**
```properties
# Global Database
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=global_user
app.sharding.global-db.password=global_password

# Shard Configuration
app.sharding.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shard1.replica1.url=jdbc:postgresql://localhost:5433/shard1_db
app.sharding.shard1.hikari.maximum-pool-size=20
app.sharding.shard1.latest=true

# Validation (same for all databases)
app.sharding.validation.strictness=STRICT
app.sharding.tenant-column-names=tenant_id,company_id
```

### Usage Example

```java
@Entity
@ShardedEntity  // Routes to tenant-specific shard
public class Customer {
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    // ... other fields
}

@Service
public class CustomerService {
    public Customer save(Customer customer) {
        return TenantContext.executeInTenantContext(customer.getTenantId(), () -> {
            return customerRepository.save(customer);
        });
    }
}
```

## 🆕 Sample Sharded Application

**Location**: `sample-sharded-app/`
**Purpose**: Complete demonstration of the sharding library

### Features Demonstrated
- Sharded entities (`Customer`) with tenant isolation
- Non-sharded entities (`GlobalConfig`) in global database
- REST API with tenant context management
- Database setup scripts with sample data
- Configuration examples

### API Endpoints
```
POST /api/customers/set-tenant/{tenantId}  # Set tenant context
POST /api/customers                        # Create customer
GET  /api/customers                        # Get customers for tenant
GET  /api/customers/{id}                   # Get specific customer
PUT  /api/customers/{id}                   # Update customer
DELETE /api/customers/{id}                 # Delete customer
```

## 📊 Technical Specifications

### Validation Levels
- **STRICT**: Throw exception if tenant_id missing (Production)
- **WARN**: Log warning but proceed (Development)
- **LOG**: Info logging only (Testing)
- **DISABLED**: No validation (Not recommended)

### Database Support & Optimization

#### Supported Databases
- ✅ **MySQL 5.7+** - Full support with MySQL-specific optimizations
- ✅ **PostgreSQL 11+** - Full support with PostgreSQL-specific optimizations
- 🔄 **SQL Server** - Planned for future release
- 🔄 **Oracle** - Planned for future release

#### HikariCP Database-Specific Optimizations
- **MySQL**: Prepared statement caching, server-side preparation, UTF8MB4 charset support
- **PostgreSQL**: Statement cache optimization, prepared statement thresholds, cache size tuning
- **SQL Server**: Statement pooling configuration *(planned)*
- **Oracle**: Implicit statement caching *(planned)*

#### Automatic Database Detection
The library automatically detects the database type from JDBC URLs and applies appropriate SQL syntax and optimizations:
- **MySQL**: Uses `DATABASE()` function and MySQL-specific table creation syntax
- **PostgreSQL**: Uses `current_schema()` function and PostgreSQL-specific syntax
- **Fallback**: Defaults to MySQL provider if detection fails

### Connection Pool Defaults
- `maximum-pool-size`: 20 (balanced for production)
- `minimum-idle`: 5 (maintain ready connections)
- `connection-timeout`: 30s (standard timeout)
- `idle-timeout`: 10m (cleanup idle connections)
- `max-lifetime`: 30m (connection refresh)

### Replica Selection Strategies
- **ROUND_ROBIN**: Distribute load across replicas
- **RANDOM**: Random replica selection
- **FIRST_AVAILABLE**: Always use first replica

## 🔧 Development Workflow

### Entity Development
1. Annotate entity with `@ShardedEntity`
2. Include tenant column (`tenant_id` or `company_id`)
3. Library validates at startup and runtime

### Query Development
1. Queries automatically validated for tenant filtering
2. Configurable strictness prevents data leakage
3. Clear error messages for missing tenant conditions

### Background Jobs
```java
@Component
public class ReportGenerator {

    private final TenantIterator tenantIterator;

    public void generateReports() {
        tenantIterator.processAllTenants(this::generateReport, 10); // Batch size 10
    }

    private void generateReport(Long tenantId) {
        // Tenant context automatically set
        // Database operations routed to correct shard
    }
}
```

### Monitoring
```java
@RestController
public class ShardingMonitorController {

    @GetMapping("/sharding/stats")
    public ShardStatistics getStats() {
        return shardUtils.getShardStatistics();
    }
}
```

## 🚀 Production Deployment

### Database Setup
1. **Global Database**: Contains `tenant_shard_mapping` and global entities (supports MySQL and PostgreSQL)
2. **Shard Databases**: Contains tenant-specific data with replicas (supports MySQL and PostgreSQL)
3. **Connection Pools**: Optimized per database type and load with automatic database detection
4. **Schema Management**: Automatic table creation with database-specific syntax and optimizations

### Configuration Management
- Externalize database credentials
- Configure appropriate pool sizes per environment
- Set validation strictness to STRICT in production
- Enable JMX monitoring for observability

### Scalability
- **Horizontal**: Add new shards for capacity
- **Vertical**: Scale individual shard resources
- **Replica Scaling**: Add read replicas for read-heavy workloads
- **Tenant Balancing**: Use latest shard feature for growth

## 📈 Benefits

### For Developers
✅ **Zero Configuration** - Auto-configuration with sensible defaults
✅ **Type Safety** - Compile-time validation of entity annotations
✅ **Clear Errors** - Helpful error messages and validation
✅ **Flexible Validation** - Configurable strictness levels
✅ **Rich APIs** - Comprehensive utilities for shard management
✅ **Database Agnostic** - Seamless MySQL and PostgreSQL support with automatic detection

### For Operations
✅ **Performance** - Database-specific optimizations for MySQL and PostgreSQL
✅ **Monitoring** - JMX metrics and health endpoints
✅ **Scalability** - Easy shard addition and tenant balancing
✅ **Reliability** - Connection pooling and replica support
✅ **Security** - Query validation prevents data leakage
✅ **Database Flexibility** - Switch between MySQL and PostgreSQL without code changes

### For Business
✅ **Multi-Tenancy** - Complete tenant isolation
✅ **Scalability** - Handle millions of tenants across shards
✅ **Performance** - Optimized database operations
✅ **Compliance** - Data isolation and tenant security
✅ **Cost Efficiency** - Resource optimization per shard

## 🔮 Future Enhancements

Potential areas for extension:
- Cross-shard joins and distributed queries
- Automated shard rebalancing
- Read replica auto-failover
- GraphQL integration
- Distributed transaction support
- Tenant migration utilities
- Performance analytics dashboard

---

This project provides a production-ready foundation for multi-tenant applications requiring sophisticated database sharding with comprehensive Spring Boot integration.
