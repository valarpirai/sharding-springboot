# Architecture

## Two-Database Model

Every deployment has exactly two tiers of databases:

```
┌─────────────────────────┐      ┌──────────────────────────────┐
│      Global Database    │      │        Shard Databases        │
│  (single, centralized)  │      │   (many, one per shard)       │
├─────────────────────────┤      ├──────────────────────────────┤
│ tenant_shard_mapping    │      │ shard1-master                │
│ accounts                │      │   └── shard1-replica1        │
│ (other global entities) │      │   └── shard1-replica2        │
└─────────────────────────┘      │ shard2-master                │
                                 │   └── shard2-replica1        │
                                 └──────────────────────────────┘
```

- **Global DB**: stores the `tenant_shard_mapping` table (tenant_id → shard_id) and all non-sharded entities
- **Shard DBs**: store tenant-specific data; each shard has one master (writes) and zero or more replicas (reads)

---

## Request Flow

```
HTTP Request
    │
    ▼
ShardSelectorFilter
    │  extracts tenant ID from header (account-id)
    │  calls shardingFacade.resolveAndSetTenantContext(tenantId, readOnly)
    │
    ▼
ShardResolutionService.resolveTenantInfo(tenantId, readOnly)
    │  looks up tenant_shard_mapping in global DB (cached)
    │  calls ShardDataSourceRouter.getShardDataSource(shardId, readOnly)
    │  returns TenantInfo { tenantId, shardId, readOnly, DataSource }
    │
    ▼
TenantContext.setTenantInfo(TenantInfo)
    │  stores TenantInfo in ThreadLocal
    │
    ▼
JPA / JDBC Operation
    │
    ▼
RoutingDataSource.determineTargetDataSource()
    │  reads TenantContext → returns pre-resolved shard DataSource
    │  (non-sharded entity → returns globalDataSource)
    │
    ▼
Database Query Executed
    │
    ▼
ShardSelectorFilter (finally)
    └── TenantContext.clear()
```

---

## Auto-Configuration Chain

`ShardingAutoConfiguration` is the entry point (imported via `spring.factories`). It wires all beans and imports two sub-configurations:

```
ShardingAutoConfiguration
├── globalDataSource              (HikariCP → global DB)
├── globalJdbcTemplate
├── DatabaseSqlProviderFactory    (auto-detects PostgreSQL/MySQL/H2 from JDBC URL)
│     └── H2/MySQL/PostgreSQLSqlProvider  (extend AbstractSqlProvider)
├── ITenantShardMappingRepo       (@ConditionalOnMissingBean → replaceable)
│     └── TenantShardMappingRepository  (default JDBC implementation)
├── ShardConfigService            (reads ShardingConfigProperties only, no I/O)
├── TenantAssignmentService       (tenant-to-shard lookups + assignments via repo)
├── ShardResolutionService        (resolves TenantInfo with pre-resolved DataSource)
├── ShardDataSourceRouter         (master/replica selection, holds shard DataSource map)
├── shardDataSources              (Map<shardId, ShardDataSources>)
│     └── ShardDataSources        (master DataSource + replica DataSources per shard)
├── RoutingDataSource             (@Primary DataSource — reads DataSource from TenantContext)
├── ShardingFacade                (public facade over the three shard services)
├── TenantIterator                (batch processing across all tenants)
├── EntityValidator               (startup check for @ShardedEntity)
├── ShardingConfigurationValidator
│
├── CacheConfiguration            (Caffeine or Redis)
│
└── ShardingJpaAutoConfiguration  (@ConditionalOnProperty dual-datasource.enabled)
      ├── shardedEntityManagerFactory  (sharded packages → @Primary RoutingDataSource)
      ├── globalEntityManagerFactory   (global packages → globalDataSource)
      ├── shardedTransactionManager
      └── globalTransactionManager
```

All beans use `@ConditionalOnMissingBean` — any bean can be overridden by declaring a replacement in your application context.

---

## Key Classes

### Context Layer (`context/`)

| Class | Role |
|-------|------|
| `TenantContext` | ThreadLocal store; holds one `TenantInfo` per thread |
| `TenantInfo` | Immutable record: `(tenantId, shardId, readOnlyMode, shardDataSource)` — all fields required |

`TenantInfo` enforces that the shard `DataSource` is always pre-resolved. Creating a `TenantInfo` without a DataSource throws `IllegalArgumentException` at construction time.

### Lookup Layer (`lookup/`)

| Class | Role |
|-------|------|
| `ITenantShardMappingReadRepo` | Read-only interface; routing and iteration consumers depend on this |
| `ITenantShardMappingRepo` | Full interface (read + write + cache); extends the read interface |
| `TenantShardMappingRepository` | Default JDBC implementation of `ITenantShardMappingRepo` |
| `ShardConfigService` | Reads `ShardingConfigProperties`; no I/O |
| `TenantAssignmentService` | Tenant-to-shard lookups, assignments, and distribution queries |
| `ShardResolutionService` | Resolves `TenantInfo` with pre-resolved shard `DataSource`; sets `TenantContext` |
| `ShardingFacade` | Public-facing Spring `@Component`; delegates to the three focused services above |
| `DatabaseSqlProviderFactory` | Picks the right `DatabaseSqlProvider` by JDBC URL; extensible via `@Component` |
| `AbstractSqlProvider` | Base class with shared DDL for `tenant_shard_mapping`; subclasses add DB-specific clauses |

### Routing Layer (`routing/`)

| Class | Role |
|-------|------|
| `RoutingDataSource` | `@Primary` `AbstractDataSource`; reads pre-resolved `DataSource` from `TenantContext` on every `getConnection()` |
| `ShardDataSourceRouter` | Holds master + replica `DataSource` map per shard; selects master or replica based on `readOnlyMode` |
| `ShardDataSources` | Container for one shard's master + replica DataSources; round-robin replica selection |

### Migration Layer (`migration/`)

| Class | Role |
|-------|------|
| `LiquibaseMigrationOrchestrator` | Executes Liquibase across all shards; supports SEQUENTIAL/PARALLEL/WAVE/CANARY strategies |
| `MigrationLockManager` | Application-level lock prevents concurrent migration runs |
| `MigrationProgressTracker` | In-memory per-shard progress state |
| `MigrationValidator` | Pre-flight checks before running migrations |

### Supporting

| Class | Role |
|-------|------|
| `TenantContextTaskDecorator` | Copies `TenantInfo` from calling thread into `@Async` thread pool threads |
| `TenantIterator` | Batch/async processing across all active tenants; handles context setup per tenant |
| `EntityValidator` | Startup: verifies all `@Entity` classes in sharded packages have `@ShardedEntity` and a tenant column |
| `HikariConfigFactory` | Creates and validates `HikariConfig` objects with database-specific pool defaults |

---

## Package Structure (starter module)

```
com.valarpirai.sharding/
├── annotation/       @ShardedEntity marker
├── async/            TenantContextTaskDecorator
├── config/           Auto-configuration, properties, HikariConfigFactory
├── context/          TenantContext, TenantInfo
├── exception/        ShardLookupException, RoutingException,
│                     EntityValidationException, TenantIteratorException,
│                     MigrationException
├── iterator/         TenantIterator
├── lookup/           ITenantShardMappingRepo (+ Read/Write/Cache sub-interfaces),
│                     TenantShardMappingRepository, ShardingFacade,
│                     ShardConfigService, TenantAssignmentService,
│                     ShardResolutionService, DatabaseSqlProvider implementations
├── migration/        LiquibaseMigrationOrchestrator and supporting classes
├── routing/          ShardDataSourceRouter, RoutingDataSource, ShardDataSources
└── validation/       EntityValidator
```

---

## Dual DataSource — Package-Based Entity Routing

`ShardingJpaAutoConfiguration` creates two separate `EntityManagerFactory` instances, each scanning a different package:

```
app.sharding.dual-datasource.global-entity-base-package   → globalEntityManagerFactory
app.sharding.dual-datasource.sharded-entity-base-package  → shardedEntityManagerFactory
```

Both EMFs use the `@Primary RoutingDataSource`. The routing DataSource returns the global DataSource when no tenant context is set (global entity operations), and the pre-resolved shard DataSource when `TenantContext` is populated.

Repository interfaces must live in the matching package:
```
app.sharding.dual-datasource.global-repository-base-package   → uses globalDataSource
app.sharding.dual-datasource.sharded-repository-base-package  → uses RoutingDataSource
```

Wrong package → wrong `EntityManagerFactory` → `NoTransactionException` or silent writes to the wrong database at runtime.

---

## Extension Points

| What to replace | How |
|-----------------|-----|
| Tenant-to-shard lookup (DB, external API, config file) | Implement `ITenantShardMappingRepo`, declare as `@Bean` — default disabled via `@ConditionalOnMissingBean` |
| Add a new database type (DDL generation) | Create a `@Component` implementing `DatabaseSqlProvider` with `@Order`; no factory changes needed |
| Any infrastructure bean | Declare a `@Bean` of the same type in your application context |
| Cache backend | Set `app.sharding.cache.type=REDIS` and configure Redis properties |
| Async thread pool | Declare a `TaskExecutor` bean with `TenantContextTaskDecorator` attached |

---

## Read/Write Splitting

`ShardDataSources` holds replicas in a list. `ShardDataSourceRouter` selects:
- `readOnlyMode = false` → master DataSource
- `readOnlyMode = true` → replica (round-robin; falls back to master if none configured)

Set `readOnlyMode` via `ShardingFacade`:
```java
// write (master)
shardingFacade.resolveTenantInfo(tenantId, false)

// read (replica)
shardingFacade.resolveTenantInfo(tenantId, true)
```
