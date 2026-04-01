# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-tenant database sharding library for Spring Boot with a sample demonstration application. The project consists of two Maven modules:

1. **sharding-springboot-starter**: Production-ready sharding library (Spring Boot starter)
2. **sample-sharded-app**: Demo application showcasing the library

**Tech Stack**: Java 21, Spring Boot 3.4.5, Lombok, HikariCP, Liquibase, PostgreSQL/MySQL

## Build & Test Commands

```bash
# Build entire project (all modules)
mvn clean install

# Build without tests (faster)
mvn clean install -DskipTests

# Build single module
cd sharding-springboot-starter && mvn clean install
cd sample-sharded-app && mvn clean package

# Run all tests
mvn test

# Run specific test
mvn test -Dtest=TenantContextTest
mvn test -Dtest=ShardingFunctionalityTest

# Run integration tests only
mvn test -Dtest="*IT"

# Code formatting with Spotless
mvn spotless:check    # Check formatting
mvn spotless:apply    # Apply formatting

# Run sample application
cd sample-sharded-app && mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev,redis
```

## Module Architecture

### sharding-springboot-starter (Core Library)

**Package Structure:**
- `config/` - Auto-configuration, properties, validation, dual DataSource setup
- `context/` - TenantContext (thread-local), TenantInfo (immutable holder)
- `async/` - TenantContextTaskDecorator for async context propagation
- `lookup/` - Directory-based tenant→shard mapping, DatabaseSqlProvider for DB-specific SQL
- `routing/` - Connection routing, master-replica selection
- `validation/` - Query validation (SQL tenant filtering), entity validation (@ShardedEntity)
- `annotation/` - @ShardedEntity marker
- `migration/` - Liquibase orchestration (sequential, parallel, wave, canary strategies)

**Key Components:**
- `ShardingAutoConfiguration`: Main auto-config entry point
- `ShardingJpaAutoConfiguration`: Dual DataSource configuration with package-based entity routing
- `TenantShardMappingRepository`: Manages `tenant_shard_mapping` table in global DB
- `ShardAwareDataSourceDelegate`: Routes connections based on TenantContext
- `DatabaseSqlProviderFactory`: Auto-detects DB type (MySQL/PostgreSQL/H2) from JDBC URL

### sample-sharded-app (Demo Application)

**Package Structure:**
- `entity.global/` - Non-sharded entities (e.g., Account) stored in global DB
- `entity.sharded/` - Sharded entities with @ShardedEntity (e.g., User, Ticket)
- `repository.global/` - Repositories for global entities
- `repository.sharded/` - Repositories for sharded entities
- `service/` - Business logic with TenantContext management
- `controller/` - REST APIs with tenant context handling
- `security/` - JWT-based authentication with tenant extraction

**Important**: Package-based routing is configured via `app.sharding.dual-datasource.*` properties.

## Database Architecture

### Two-Database Model

1. **Global DB**: 
   - Contains `tenant_shard_mapping` (tenant_id → shard_id mapping)
   - Stores non-sharded entities (e.g., Account, global configs)
   - Single centralized database

2. **Shard DBs**:
   - Multiple databases, each with master + replicas
   - Contains tenant-specific data (entities with @ShardedEntity)
   - Read-write splitting: writes→master, reads→replicas (round-robin/random/first)

### Database Setup

```bash
# PostgreSQL setup (default)
cd sample-sharded-app
psql -U postgres -f database-setup.sql

# Check DATABASE_SETUP.md for detailed instructions
```

## Configuration Patterns

### Sharding Configuration Structure

All properties use `app.sharding.*` prefix:

```properties
# Global DB (required)
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=...
app.sharding.global-db.password=...

# Shard configuration (repeatable per shard)
app.sharding.shards.{shardId}.master.url=...
app.sharding.shards.{shardId}.replicas.{replicaId}.url=...
app.sharding.shards.{shardId}.latest=true|false  # Latest shard gets new tenants
app.sharding.shards.{shardId}.status=ACTIVE|MAINTENANCE|READONLY

# Validation
app.sharding.validation.strictness=STRICT|WARN|LOG|DISABLED
app.sharding.tenant-column-names=tenant_id,company_id,account_id

# Dual DataSource (package-based routing)
app.sharding.dual-datasource.enabled=true
app.sharding.dual-datasource.global-repository-base-package=com.example.repository.global
app.sharding.dual-datasource.sharded-repository-base-package=com.example.repository.sharded
```

### Migration Configuration

```properties
app.sharding.migration.enabled=true
app.sharding.migration.global-change-log-path=db/changelog/global/master-changelog.xml
app.sharding.migration.sharded-change-log-path=db/changelog/sharded/master-changelog.xml
app.sharding.migration.default-strategy=WAVE  # or SEQUENTIAL, PARALLEL, CANARY
```

## Important Conventions

### Tenant Context Management

All sharded operations MUST execute within TenantContext:

```java
// Correct pattern
TenantContext.executeInTenantContext(tenantId, () -> {
    return repository.save(entity);
});

// Or using try-with-resources
try (TenantContext.TenantScope scope = TenantContext.setCurrentTenant(tenantId)) {
    repository.findAll();
}
```

### Async Context Propagation

For `@Async` methods and async operations, use `TenantContextTaskDecorator` to propagate tenant context across thread boundaries:

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new TenantContextTaskDecorator());
    return executor;
}

@Async("taskExecutor")
public CompletableFuture<Result> asyncOperation(Long tenantId) {
    // Tenant context automatically propagated
    return CompletableFuture.completedFuture(repository.findAll());
}
```

**Important**: Context must be set in calling thread before async execution. If not set, manually resolve with `ShardUtils.resolveTenantInfo()` inside async method.

### Entity Annotations

- **Sharded entities**: MUST have `@ShardedEntity` annotation + tenant_id column
- **Global entities**: NO @ShardedEntity annotation, stored in global DB
- Validation occurs at startup (EntityValidator checks all @Entity classes)

### Query Validation

- STRICT mode (production): Throws exception if SELECT/UPDATE/DELETE missing tenant_id filter
- Configured via `app.sharding.validation.strictness`
- Tenant column names: `app.sharding.tenant-column-names` (comma-separated)

### Package-Based Routing

Repository interfaces MUST be in the correct package:
- Global repos → `app.sharding.dual-datasource.global-repository-base-package`
- Sharded repos → `app.sharding.dual-datasource.sharded-repository-base-package`

Wrong package = wrong DataSource = runtime errors.

## Testing Strategy

### Unit Tests
- Located in `sharding-springboot-starter/src/test/java`
- Use H2 in-memory database
- Mock-based for isolated component testing

### Integration Tests
- Located in `sample-sharded-app/src/test/java/com/valarpirai/example/integration`
- Use TestContainers for PostgreSQL
- Extend `BaseIntegrationTest` for common setup
- Test categories:
  - `MultiTenantDataIsolationTest`: Tenant data isolation
  - `CrossTenantSecurityTest`: Cross-tenant access prevention
  - `LiquibaseMigrationOrchestratorTest`: Migration strategies
  - API tests: `*ControllerApiTest`

### Running Integration Tests

```bash
# Requires Docker running (TestContainers)
mvn test -Dtest="*IT"
mvn test -Dtest=MultiTenantDataIsolationTest
```

## Database Support

Currently supported:
- **PostgreSQL 11+**: Primary support, full optimization
- **MySQL 5.7+**: Full support with MySQL-specific optimizations
- **H2**: Testing only

Database type auto-detected from JDBC URL. Database-specific SQL via `DatabaseSqlProvider` implementations.

## Common Development Patterns

### Adding New Sharded Entity

1. Create entity class with `@Entity` + `@ShardedEntity`
2. Add `tenant_id` column (Long, not null)
3. Place in sharded entity package
4. Create repository in sharded repository package
5. Use TenantContext in service methods

### Adding New Shard

1. Provision new database (master + replicas)
2. Add configuration to application.properties:
   ```properties
   app.sharding.shards.shard3.master.url=...
   app.sharding.shards.shard3.latest=true
   ```
3. Run migrations: POST to `/api/migrations/run-sharded`
4. Mark as latest to receive new tenants

### Migration Workflow

1. Create Liquibase changelog in `src/main/resources/db/changelog/`
   - Global changes → `global/` directory
   - Sharded changes → `sharded/` directory
2. Test locally with dry-run mode
3. Use wave/canary strategy for production
4. Monitor via `/api/migrations/status` endpoint

## API Documentation

Sample app exposes Swagger UI:
- URL: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/v3/api-docs

## Troubleshooting

### "No tenant context set" errors
→ Wrap operations in `TenantContext.executeInTenantContext()`

### "Query validation failed: SELECT missing tenant_id"
→ Add WHERE tenant_id = ? to query, or lower strictness (not recommended for prod)

### EntityManager closed errors with dual DataSource
→ Check repository package matches configuration

### Connection pool exhausted
→ Tune HikariCP settings: `app.sharding.shards.{shardId}.hikari.maximum-pool-size`

## Key Documentation Files

- `README.md`: Project overview and quick start
- `doc/MIGRATION_GUIDE.md`: Liquibase migration strategies
- `doc/TRANSACTION_GUIDE.md`: Transaction patterns for sharded DBs
- `doc/ZERO_DOWNTIME_BEST_PRACTICES.md`: Production deployment strategies
- `doc/ACCOUNT_SIGNUP_FLOW.md`: New tenant signup implementation
- `sample-sharded-app/DATABASE_SETUP.md`: Database provisioning guide
- `sharding-springboot-starter/SPECIFICATION.md`: Technical specifications
