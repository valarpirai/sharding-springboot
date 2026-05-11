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
    │  calls shardUtils.resolveAndSetTenantContext(tenantId, readOnly)
    │
    ▼
ITenantShardMappingRepo.findShardByTenantId(tenantId)
    │  queries tenant_shard_mapping in global DB (cached)
    │  returns TenantShardMapping { tenantId, shardId }
    │
    ▼
ShardAwareDataSourceDelegate.getShardDataSource(shardId, readOnly)
    │  selects master or replica DataSource
    │
    ▼
TenantContext.setTenantInfo(TenantInfo)
    │  stores TenantInfo in ThreadLocal: { tenantId, shardId, readOnly, DataSource }
    │
    ▼
JPA / JDBC Operation
    │
    ▼
RoutingDataSource.determineTargetDataSource()
    │  reads TenantContext → returns pre-resolved shard DataSource
    │  (non-sharded entity → returns globalDataSource instead)
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
├── DatabaseSqlProviderFactory    (auto-detects PostgreSQL/MySQL/H2)
├── ITenantShardMappingRepo       (@ConditionalOnMissingBean → replaceable)
│     └── TenantShardMappingRepository (default JDBC implementation)
├── shardDataSources              (Map<shardId, ShardDataSources>)
│     └── ShardDataSources        (master DataSource + replica DataSources)
├── ShardAwareDataSourceDelegate  (routes to correct shard DataSource)
├── RoutingDataSource             (@Primary DataSource — wraps the delegate)
├── ShardUtils                    (public API for shard operations)
├── TenantIterator                (batch processing across all tenants)
├── EntityValidator               (startup check for @ShardedEntity)
├── ShardingConfigurationValidator
│
├── CacheConfiguration            (Caffeine or Redis)
│
└── ShardingJpaAutoConfiguration  (@ConditionalOnProperty dual-datasource.enabled)
      ├── shardedEntityManagerFactory   (routes sharded packages → RoutingDataSource)
      ├── globalEntityManagerFactory    (routes global packages → globalDataSource)
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
| `ITenantShardMappingRepo` | Interface — implement this to replace the default DB-backed lookup |
| `TenantShardMappingRepository` | Default implementation; queries `tenant_shard_mapping` via JDBC |
| `ShardUtils` | Public API: `resolveTenantInfo()`, `resolveAndSetTenantContext()`, `assignTenantToLatestShard()`, `getTenantsInShard()`, `getShardStatistics()` |
| `DatabaseSqlProviderFactory` | Auto-detects DB type from JDBC URL; returns DB-specific SQL |

### Routing Layer (`routing/`)

| Class | Role |
|-------|------|
| `RoutingDataSource` | Spring `AbstractRoutingDataSource`; calls `determineTargetDataSource()` on every `getConnection()` |
| `ShardAwareDataSourceDelegate` | Reads `TenantContext`; returns master or replica DataSource; falls back to global for non-sharded entities |
| `ShardDataSources` | Container for one shard's master + replica DataSources; round-robin replica selection |
| `TenantAwareDataSourceDelegate` | Used by `ShardingJpaAutoConfiguration` to separate global vs sharded JPA contexts |

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

---

## Package Structure (starter module)

```
com.valarpirai.sharding/
├── annotation/       @ShardedEntity marker
├── async/            TenantContextTaskDecorator
├── config/           Auto-configuration, properties, HikariCP utils
├── context/          TenantContext, TenantInfo
├── exception/        ShardLookupException, RoutingException,
│                     EntityValidationException, TenantIteratorException,
│                     MigrationException
├── iterator/         TenantIterator
├── lookup/           ITenantShardMappingRepo, TenantShardMappingRepository,
│                     ShardUtils, DatabaseSqlProvider implementations
├── migration/        LiquibaseMigrationOrchestrator and supporting classes
├── routing/          ShardAwareDataSourceDelegate, RoutingDataSource,
│                     ShardDataSources, TenantAwareDataSourceDelegate
└── validation/       EntityValidator
```

---

## Dual DataSource — Package-Based Entity Routing

`ShardingJpaAutoConfiguration` creates two separate `EntityManagerFactory` instances, each scanning a different package:

```
app.sharding.dual-datasource.global-entity-base-package   → globalEntityManagerFactory
app.sharding.dual-datasource.sharded-entity-base-package  → shardedEntityManagerFactory
```

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
| Tenant-to-shard lookup (DB, external API, config file) | Implement `ITenantShardMappingRepo`, declare as `@Bean` — default is auto-disabled via `@ConditionalOnMissingBean` |
| Any infrastructure bean | Declare a `@Bean` of the same type in your app context |
| Cache backend | Set `app.sharding.cache.type=REDIS` and configure Redis properties |
| Async thread pool | Declare a `TaskExecutor` bean with `TenantContextTaskDecorator` attached |

---

## Read/Write Splitting

`ShardDataSources` holds replicas in a list. `ShardAwareDataSourceDelegate` selects:
- `readOnlyMode = false` → master DataSource
- `readOnlyMode = true` → replica (round-robin across available replicas; falls back to master if none configured)

Set `readOnlyMode` when building `TenantInfo`:
```java
// write
shardUtils.resolveTenantInfo(tenantId, false)

// read
shardUtils.resolveTenantInfo(tenantId, true)
```
