# Documentation

## Core

- **[Getting Started](getting-started.md)** - Installation, configuration, basic usage, TenantIterator
- **[Architecture](architecture.md)** - Request flow, auto-configuration chain, key classes, extension points
- **[Technical Specification](specification.md)** - Full configuration property reference
- **[Production Readiness](PRODUCTION_READINESS.md)** - ⚠️ Current gaps and score (5.4/10 — NOT ready for production)
- **[Features & Roadmap](FEATURES.md)** - Completed features and development roadmap

## Guides

- **[Migrations](migrations.md)** - Liquibase strategies (sequential, parallel, wave, canary), idempotency
- **[Transactions](transactions.md)** - Transaction patterns for single and cross-DataSource operations
- **[Account Signup Flow](account-signup.md)** - Tenant onboarding: global DB, shard assignment, context setup
- **[Custom Shard Lookup](custom-shard-lookup.md)** - Custom tenant-to-shard mapping implementations

## Deployment & Testing

- **[Database Setup](database-setup.md)** - PostgreSQL/MySQL provisioning for global DB and shards
- **[Zero Downtime](zero-downtime.md)** - Safe DDL patterns and production deployment strategies
- **[Integration Tests](integration-tests.md)** - TestContainers-based test guide (69 tests)

## Archive

- [Changes Log](changes.md)
- [Spring Boot 3 Upgrade](upgrade-spring-boot-3.md)
- [Final Summary](final-summary.md)
