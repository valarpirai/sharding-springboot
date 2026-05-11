# Galaxy Sharding Spring Boot Starter Specification

## Overview
A standalone Spring Boot auto-configuration module for multi-tenancy and database sharding with replicas support.

## Key Requirements

### 1. Configuration Structure
- **Package**: `com.valarpirai.sharding`
- **Language**: Java (not Kotlin)
- **Configuration**: Flat structure in application.properties
- **Global Database**: Separate configuration for tenant_shard_mapping table

### 2. Directory-Based Sharding
- **Lookup Table**: `tenant_shard_mapping` in global database
- **Table Structure**:
  ```sql
  CREATE TABLE tenant_shard_mapping (
    tenant_id BIGINT NOT NULL,
    shard_id VARCHAR(255) NOT NULL,
    region VARCHAR(255),
    shard_status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
  );
  ```
- **Error Handling**: Throw error with table creation SQL if table doesn't exist

### 3. Configuration Properties Structure
```properties
# Global Database Configuration
app.sharding.global-db.url=jdbc:postgresql://global-db:5432/global
app.sharding.global-db.username=global_user
app.sharding.global-db.password=global_pass
app.sharding.global-db.driver-class-name=org.postgresql.Driver
app.sharding.global-db.hikari.maximum-pool-size=10
app.sharding.global-db.hikari.minimum-idle=5
app.sharding.global-db.hikari.connection-timeout=30s
app.sharding.global-db.hikari.idle-timeout=10m

# Shard Configuration — note the 'shards' segment and 'replicas' nesting
app.sharding.shards.shard1.master.url=jdbc:postgresql://shard1-master:5432/db
app.sharding.shards.shard1.master.username=shard1_user
app.sharding.shards.shard1.master.password=shard1_pass
app.sharding.shards.shard1.master.driver-class-name=org.postgresql.Driver
app.sharding.shards.shard1.replicas.replica1.url=jdbc:postgresql://shard1-replica1:5432/db
app.sharding.shards.shard1.replicas.replica1.username=shard1_user
app.sharding.shards.shard1.replicas.replica1.password=shard1_pass
app.sharding.shards.shard1.replicas.replica2.url=jdbc:postgresql://shard1-replica2:5432/db
app.sharding.shards.shard1.replicas.replica2.username=shard1_user
app.sharding.shards.shard1.replicas.replica2.password=shard1_pass
app.sharding.shards.shard1.hikari.maximum-pool-size=20
app.sharding.shards.shard1.hikari.minimum-idle=10
app.sharding.shards.shard1.latest=true
app.sharding.shards.shard1.region=us-east-1
app.sharding.shards.shard1.status=ACTIVE

app.sharding.shards.shard2.master.url=jdbc:postgresql://shard2-master:5432/db
app.sharding.shards.shard2.master.username=shard2_user
app.sharding.shards.shard2.master.password=shard2_pass
app.sharding.shards.shard2.replicas.replica1.url=jdbc:postgresql://shard2-replica1:5432/db
app.sharding.shards.shard2.replicas.replica1.username=shard2_user
app.sharding.shards.shard2.replicas.replica1.password=shard2_pass
app.sharding.shards.shard2.hikari.maximum-pool-size=20
app.sharding.shards.shard2.hikari.minimum-idle=10
app.sharding.shards.shard2.latest=false
app.sharding.shards.shard2.status=ACTIVE

# Tenant Configuration
app.sharding.tenant-column-names=tenant_id,company_id,account_id
app.sharding.validation.strictness=STRICT  # STRICT, WARN, LOG, DISABLED
```

### 4. Core Components

#### 4.1 TenantContext (Global ThreadLocal)
- Store current tenant information
- Thread-safe tenant context management
- Support for read/write operation flags

#### 4.2 Connection Routing
- Route connections based on TenantContext
- Support multiple replicas per shard
- Automatic read/write splitting
- Connection pooling with HikariCP

#### 4.3 Query Validation (DataSource Proxy)
- Intercept SQL queries at JDBC level
- Validate sharded entity queries contain tenant_id/company_id
- Configurable strictness levels:
  - **STRICT**: Throw exception if tenant_id missing
  - **WARN**: Log warning but allow query
  - **LOG**: Log info message
  - **DISABLED**: No validation
- Support for multiple tenant column names
- Case-insensitive column name matching
- Support aliases (e.g., t.tenant_id, customer.company_id)

#### 4.4 Entity Marking
- **@ShardedEntity** marker interface
- **Default behavior**: Unmarked entities are non-sharded (reside in global db)
- Entity classification logic

### 5. Additional Features

#### 5.1 Latest Shard Support
- Mark one shard as "latest" for new account signups
- Configuration flag: `app.sharding.{shardId}.latest=true`
- Utility methods to get latest shard

#### 5.2 Shard Lookup Utilities
- Utils to find shard by tenant_id
- Shard metadata retrieval
- Cache management for lookup results

#### 5.3 Tenant Iterator with Batch Mode
- Iterate over tenants for background jobs
- Batch processing support
- Configurable batch sizes
- Support for parallel processing

#### 5.4 Comprehensive HikariCP Configuration
Support all HikariCP properties:
- maximum-pool-size
- minimum-idle
- connection-timeout
- idle-timeout
- max-lifetime
- leak-detection-threshold
- connection-test-query
- validation-timeout
- And more...

### 6. Auto-Configuration
- Spring Boot auto-configuration with conditional beans
- Automatic connection pool creation
- DataSource proxy registration
- Configuration validation

### 7. Integration
- README with integration instructions
- Configuration examples
- Usage patterns
- Migration guidelines

## Architecture Patterns
- **Auto-Configuration**: Spring Boot starter pattern
- **Proxy Pattern**: DataSource interception
- **ThreadLocal Pattern**: Tenant context isolation
- **Factory Pattern**: Connection pool creation
- **Strategy Pattern**: Shard selection strategies