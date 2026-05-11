# Sharding Spring Boot Starter

Multi-tenant database sharding library for Spring Boot. Automatically routes JPA operations to the correct shard based on tenant context, with master-replica read/write splitting and Liquibase migration orchestration across shards.

## Modules

| Module | Purpose |
|--------|---------|
| `sharding-springboot-starter` | Core sharding library (Spring Boot starter) |
| `sample-sharded-app` | Demo ticket management application |

## Quick Start

```bash
# Build
mvn clean install -DskipTests

# Set up databases (PostgreSQL)
cd sample-sharded-app && psql -U postgres -f database-setup.sql

# Run sample app
mvn spring-boot:run

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

## Installation

```xml
<dependency>
    <groupId>com.valarpirai</groupId>
    <artifactId>sharding-springboot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Configuration

```properties
# Global database (tenant_shard_mapping + non-sharded entities)
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=user
app.sharding.global-db.password=pass

# Shard (repeat per shard)
app.sharding.shards.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shards.shard1.master.username=user
app.sharding.shards.shard1.master.password=pass
app.sharding.shards.shard1.replicas.replica1.url=jdbc:postgresql://localhost:5433/shard1_db
app.sharding.shards.shard1.replicas.replica1.username=user
app.sharding.shards.shard1.replicas.replica1.password=pass
app.sharding.shards.shard1.latest=true

# Query validation
app.sharding.validation.strictness=STRICT
app.sharding.tenant-column-names=tenant_id,company_id,account_id

# Package-based routing (required for dual DataSource)
app.sharding.dual-datasource.enabled=true
app.sharding.dual-datasource.global-repository-base-package=com.example.repository.global
app.sharding.dual-datasource.sharded-repository-base-package=com.example.repository.sharded
```

## Usage

### Mark sharded entities

```java
@Entity
@ShardedEntity
public class Ticket {
    @Column(nullable = false)
    private Long tenantId;  // required
}
```

Global entities (no `@ShardedEntity`) are stored in the global database.

### Set tenant context

Inject `ShardingFacade` to resolve a `TenantInfo` before any sharded operation:

```java
@Service
public class TicketService {

    @Autowired private TicketRepository repository;
    @Autowired private ShardingFacade shardingFacade;

    public Ticket create(Long tenantId, Ticket ticket) {
        TenantInfo tenantInfo = shardingFacade.resolveTenantInfo(tenantId, false)
            .orElseThrow(() -> new ShardLookupException("No active shard for tenant: " + tenantId));
        return TenantContext.executeInTenantContext(tenantInfo, () -> repository.save(ticket));
    }
}
```

For request-scoped context (filter/controller layer):

```java
shardingFacade.resolveAndSetTenantContext(tenantId, false); // false = master, true = replica
try {
    // handle request
} finally {
    TenantContext.clear();
}
```

## Key Features

- **Auto-configuration** — zero-config Spring Boot integration via `@EnableConfigurationProperties`
- **Dual DataSource** — package-based routing separates global and sharded JPA contexts
- **Read/write splitting** — writes go to master, reads go to replicas (round-robin)
- **Query validation** — SQL-level tenant filter enforcement (STRICT / WARN / LOG / DISABLED)
- **Entity validation** — `@ShardedEntity` annotation checked at startup
- **Migration orchestration** — Liquibase across all shards (SEQUENTIAL / PARALLEL / WAVE / CANARY)
- **Async propagation** — `TenantContextTaskDecorator` copies context into `@Async` thread pools
- **Batch processing** — `TenantIterator` runs background jobs across all active tenants
- **Replaceable lookup** — implement `ITenantShardMappingRepo` to use any backing store
- **Database support** — PostgreSQL 11+ and MySQL 5.7+ with auto-detected optimizations

## Tech Stack

Java 21 · Spring Boot 3.4.5 · HikariCP · Liquibase · PostgreSQL / MySQL · TestContainers

## Documentation

See [`docs/`](docs/) for the full documentation index.

| Doc | Contents |
|-----|----------|
| [Architecture](docs/architecture.md) | Request flow, bean wiring, key classes, extension points |
| [Getting Started](docs/getting-started.md) | Installation, configuration, TenantIterator |
| [Migrations](docs/migrations.md) | Strategies, rollback, idempotency |
| [Transactions](docs/transactions.md) | Single and cross-DataSource patterns |
| [Zero Downtime](docs/zero-downtime.md) | Safe DDL, lock management, deployment strategies |
| [Integration Tests](docs/integration-tests.md) | TestContainers setup, writing new tests |
| [Production Readiness](docs/PRODUCTION_READINESS.md) | Current gaps — score 5.4/10, NOT production ready |

## License

MIT License. See `LICENSE` for details.
