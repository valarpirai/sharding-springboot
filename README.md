# Sharding Spring Boot Starter

Production-ready multi-tenant database sharding solution with Spring Boot auto-configuration.

## Project Structure

```
sharding-springboot/
├── sharding-springboot-starter/   # Core sharding library
├── sample-sharded-app/            # Demo ticket management application
└── docs/                          # Comprehensive documentation
```

## Key Features

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
✅ **Dual DataSource Configuration** - Automatic separation of global and sharded entities with package-based routing

### Architecture Components

#### 📁 Configuration (`/config/`)
- `ShardingConfigProperties` - Main configuration with flat structure
- `HikariConfigProperties` - Comprehensive HikariCP settings
- `ShardingAutoConfiguration` - Spring Boot auto-configuration
- `ShardingDataSourceAutoConfiguration` - Dual DataSource auto-configuration with Lombok-based properties
- `DualDataSourceProperties` - Package-based routing configuration (Lombok)
- `HikariConfigUtil` - Optimal defaults and database-specific optimizations

#### 🎯 Context Management (`/context/`)
- `TenantContext` - Thread-local tenant information
- `TenantInfo` - Immutable tenant data holder

#### 🔍 Shard Lookup (`/lookup/`)
- `TenantShardMappingRepository` - Directory-based tenant-shard mapping
- `TenantShardMapping` - Data model for lookup table
- `ShardUtils` - Comprehensive shard management utilities
- `DatabaseSqlProvider` - Interface for database-specific SQL operations
- `MySQLSqlProvider` - MySQL-specific SQL implementation
- `PostgreSQLSqlProvider` - PostgreSQL-specific SQL implementation
- `DatabaseSqlProviderFactory` - Automatic database detection and provider selection

#### 🔄 Connection Routing (`/routing/`)
- `ShardAwareDataSourceDelegate` - Routes connections based on tenant context
- `RoutingDataSource` - Spring DataSource integration
- `RoutingDataSource` - Enhanced routing DataSource with dual configuration support
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

**PostgreSQL Configuration (Default):**
```properties
# Global Database
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=global_user
app.sharding.global-db.password=global_password

# Shard Configuration
app.sharding.shards.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shards.shard1.replicas.replica1.url=jdbc:postgresql://localhost:5433/shard1_db
app.sharding.shards.shard1.hikari.maximum-pool-size=20
app.sharding.shards.shard1.latest=true

# Validation
app.sharding.validation.strictness=STRICT
app.sharding.tenant-column-names=tenant_id,company_id
```

**Alternative Database Support:**
- Full MySQL 5.7+ support with optimizations
- Full PostgreSQL 11+ support with optimizations
- Automatic database type detection from JDBC URLs

### Maven Dependency

Add the sharding starter to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.valarpirai</groupId>
    <artifactId>sharding-springboot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
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

## Quick Start

```bash
# 1. Build the library
mvn clean install

# 2. Set up PostgreSQL databases
cd sample-sharded-app
psql -U postgres -f database-setup.sql

# 3. Run the sample application
mvn spring-boot:run

# 4. Access Swagger UI
open http://localhost:8080/swagger-ui.html
```

## Documentation

📚 **[Complete Documentation](docs/)** - Comprehensive guides, deployment, testing, and reference

### Quick Links

- **[Getting Started](docs/guides/getting-started.md)** - Installation and basic usage
- **[Migrations Guide](docs/guides/migrations.md)** - Database schema changes across shards
- **[Transactions Guide](docs/guides/transactions.md)** - Transaction patterns
- **[Zero Downtime Deployment](docs/deployment/zero-downtime.md)** - Production best practices
- **[Integration Tests](docs/testing/integration-tests.md)** - Testing guide (69 comprehensive tests)
- **[Technical Specification](docs/reference/specification.md)** - Complete library reference

## Basic Usage

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.valarpirai</groupId>
    <artifactId>sharding-springboot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure

```properties
# Global Database
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=user
app.sharding.global-db.password=pass

# Shard
app.sharding.shards.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shards.shard1.master.username=user
app.sharding.shards.shard1.master.password=pass
app.sharding.shards.shard1.latest=true
```

### 3. Mark Sharded Entities

```java
@Entity
@ShardedEntity
public class Customer {
    @Column(nullable = false)
    private Long tenantId;  // Required
    
    private String name;
}
```

### 4. Use Tenant Context

```java
@Service
public class CustomerService {
    public Customer save(Long tenantId, Customer customer) {
        return TenantContext.executeInTenantContext(tenantId, () -> {
            return customerRepository.save(customer);
        });
    }
}
```

## Architecture

### Two-Database Model

1. **Global DB**: Central database for tenant-shard mappings and global entities
2. **Shard DBs**: Multiple databases with tenant-specific data (master + replicas)

### Package-Based Routing

- Global entities/repos → `*.entity.global`, `*.repository.global`
- Sharded entities/repos → `*.entity.sharded`, `*.repository.sharded`

### Migration Strategies

- **SEQUENTIAL**: One shard at a time (safest)
- **PARALLEL**: All shards simultaneously (fastest)
- **WAVE**: Batches with delays (recommended for production)
- **CANARY**: Test on one shard first (critical changes)

## Benefits

**For Developers:**
- ✅ Zero-configuration Spring Boot integration
- ✅ Type-safe entity validation at compile time
- ✅ Database-agnostic (PostgreSQL, MySQL with auto-detection)
- ✅ Comprehensive testing suite (69 integration tests)

**For Operations:**
- ✅ Multiple migration strategies (sequential, parallel, wave, canary)
- ✅ Database-specific HikariCP optimizations
- ✅ JMX metrics and health endpoints
- ✅ Read-write splitting with replica support

**For Business:**
- ✅ Complete tenant data isolation
- ✅ Horizontal scalability (add shards as needed)
- ✅ Production-ready with zero-downtime deployment patterns
- ✅ Compliance-friendly with strict query validation

## Sample Application

The `sample-sharded-app` demonstrates a multi-tenant ticket management system:

- **Account signup** with automatic demo environment setup
- **JWT authentication** with tenant validation
- **User and ticket management** with role-based permissions
- **Liquibase migrations** across global DB and shards
- **Comprehensive tests** including API, security, and migration tests

**API Documentation**: http://localhost:8080/swagger-ui.html (when running)

## Tech Stack

- **Java 21**
- **Spring Boot 3.4.5**
- **Hibernate/JPA**
- **HikariCP** (connection pooling)
- **Liquibase** (schema migrations)
- **PostgreSQL** / **MySQL** (auto-detected)
- **TestContainers** (integration tests)

## License

Licensed under the MIT License. See `LICENSE` file for details.

---

**For complete documentation, see [`docs/`](docs/) directory.**
