# Documentation

Comprehensive documentation for the sharding-springboot-starter library and sample application.

## Getting Started

- **[Getting Started](guides/getting-started.md)** - Quick start guide, installation, and basic usage
- **[Database Setup](deployment/database-setup.md)** - Database provisioning and configuration

## Guides

### Core Functionality
- **[Migrations](guides/migrations.md)** - Liquibase schema migrations across shards (strategies: sequential, parallel, wave, canary)
- **[Transactions](guides/transactions.md)** - Transaction patterns for single and cross-DataSource operations
- **[Custom Shard Lookup](guides/custom-shard-lookup.md)** - Implementing custom tenant-to-shard mapping strategies

### Implementation Patterns
- **[Account Signup Flow](guides/account-signup.md)** - Complete tenant onboarding implementation with demo data setup

## Deployment

- **[Zero Downtime Best Practices](deployment/zero-downtime.md)** - Production deployment strategies inspired by PayPal patterns
- **[Database Setup](deployment/database-setup.md)** - PostgreSQL/MySQL setup for global DB and shards

## Concepts

- **[Idempotency](concepts/idempotency.md)** - Understanding migration idempotency and safe re-runs

## Testing

- **[Integration Tests](testing/integration-tests.md)** - Comprehensive guide to TestContainers-based integration testing (69 tests covering isolation, security, migrations)

## Reference

- **[Technical Specification](reference/specification.md)** - Complete library specifications, configuration options, and architecture details

## Archive

Historical documentation (for reference only):
- [Changes Log](archive/changes.md) - Detailed change history
- [Spring Boot 3 Upgrade](archive/upgrade-spring-boot-3.md) - Migration notes from Spring Boot 2.x to 3.x
- [Final Summary](archive/final-summary.md) - Project completion summary

## Quick Links

### Common Tasks

**Building the project:**
```bash
mvn clean install
mvn clean install -DskipTests  # Skip tests
```

**Running tests:**
```bash
mvn test                              # All tests
mvn test -Dtest=ClassName             # Specific test
mvn test -Dtest="*IT"                 # Integration tests only
```

**Running the sample app:**
```bash
cd sample-sharded-app
mvn spring-boot:run
```

**Database setup:**
```bash
cd sample-sharded-app
psql -U postgres -f database-setup.sql
```

### Configuration Reference

**Minimal configuration:**
```properties
# Global DB
app.sharding.global-db.url=jdbc:postgresql://localhost:5432/global_db
app.sharding.global-db.username=user
app.sharding.global-db.password=pass

# Shard
app.sharding.shards.shard1.master.url=jdbc:postgresql://localhost:5432/shard1_db
app.sharding.shards.shard1.master.username=user
app.sharding.shards.shard1.master.password=pass
app.sharding.shards.shard1.latest=true

# Validation
app.sharding.validation.strictness=STRICT
app.sharding.tenant-column-names=tenant_id
```

### Key Patterns

**Using tenant context:**
```java
TenantContext.executeInTenantContext(tenantId, () -> {
    return repository.findAll();
});
```

**Marking sharded entities:**
```java
@Entity
@ShardedEntity
public class MyEntity {
    @Column(nullable = false)
    private Long tenantId;
}
```

**Package-based routing:**
- Global entities/repos → `*.entity.global`, `*.repository.global`
- Sharded entities/repos → `*.entity.sharded`, `*.repository.sharded`

## Documentation Organization

```
docs/
├── README.md                     # This file (index)
├── guides/                       # Implementation guides
│   ├── getting-started.md
│   ├── migrations.md
│   ├── transactions.md
│   ├── account-signup.md
│   └── custom-shard-lookup.md
├── deployment/                   # Production deployment
│   ├── database-setup.md
│   └── zero-downtime.md
├── concepts/                     # Core concepts
│   └── idempotency.md
├── testing/                      # Testing guides
│   └── integration-tests.md
├── reference/                    # Technical reference
│   └── specification.md
└── archive/                      # Historical docs
    ├── changes.md
    ├── upgrade-spring-boot-3.md
    └── final-summary.md
```

## Support

For issues or questions:
1. Check the relevant guide in this documentation
2. Review `CLAUDE.md` in the project root for development patterns
3. Examine the sample application for working examples
4. Check application logs for detailed error messages

## Contributing

When adding documentation:
- Place in appropriate category (guides, deployment, etc.)
- Update this index
- Follow existing formatting and style
- Include code examples where applicable
- Keep content concise and actionable
