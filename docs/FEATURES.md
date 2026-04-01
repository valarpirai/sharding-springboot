# Features & Roadmap

Track completed features, in-progress work, and pending improvements for the sharding-springboot-starter library.

---

## ✅ Completed Features

### Core Sharding
- [x] Directory-based tenant-to-shard mapping
- [x] Dual DataSource configuration (global + sharded)
- [x] Package-based entity routing
- [x] Master-replica connection routing
- [x] Read-write splitting with replica selection
- [x] TenantContext (thread-local) management
- [x] @ShardedEntity annotation validation at startup
- [x] Query validation with configurable strictness (STRICT/WARN/LOG/DISABLED)

### Database Support
- [x] PostgreSQL 11+ with optimizations
- [x] MySQL 5.7+ with optimizations
- [x] H2 for testing
- [x] Database-specific SQL providers (auto-detected)
- [x] HikariCP connection pooling with database-specific tuning

### Migration & Schema Management
- [x] Liquibase integration for schema migrations
- [x] Multiple migration strategies (SEQUENTIAL, PARALLEL, WAVE, CANARY)
- [x] Global and sharded database migrations
- [x] Migration progress tracking
- [x] Idempotent migration execution
- [x] Migration rollback support (configurable)

### Caching
- [x] Tenant-shard mapping cache (Caffeine)
- [x] Optional Redis distributed cache support
- [x] Cache warm-up utilities
- [x] Cache eviction and TTL configuration

### Developer Experience
- [x] Spring Boot auto-configuration
- [x] Zero-config setup with sensible defaults
- [x] Comprehensive error messages
- [x] TenantContext helper methods (executeInTenantContext)
- [x] ShardUtils for manual shard operations

### Async Support
- [x] TenantContextTaskDecorator for async context propagation
- [x] CompletableFuture support
- [x] @Async method support
- [x] Thread pool integration

### Testing
- [x] 69 comprehensive integration tests
- [x] TestContainers-based testing
- [x] Multi-tenant data isolation tests
- [x] Cross-tenant security tests
- [x] Migration orchestrator tests
- [x] API endpoint tests

### Documentation
- [x] Complete documentation structure
- [x] Getting started guide
- [x] Migration guide
- [x] Transaction patterns guide
- [x] Zero-downtime deployment guide
- [x] Integration testing guide
- [x] CLAUDE.md for AI assistance

### Code Quality
- [x] Spotless Maven plugin for code formatting
- [x] Google Java Format style
- [x] Automatic import ordering

---

## 🚧 Pending Items

### Priority 0: Critical (Do First)

#### Observability
- [ ] **Spring Boot Actuator Health Indicators**
  - Per-shard database connectivity health
  - Connection pool health (active/idle/waiting)
  - Shard status health check (ACTIVE/MAINTENANCE/READONLY)
  - Tenant-shard mapping cache health
  - Migration status health indicator

- [ ] **Basic Metrics Endpoints**
  - Connection pool metrics per shard
  - Query execution time tracking
  - Tenant context lookup latency
  - Cache hit/miss rates

#### Resilience
- [ ] **Connection Leak Detection**
  - Automatic detection of unclosed connections
  - Leak reporting and alerting
  - Configurable leak threshold
  - Connection lifecycle tracking

- [ ] **Circuit Breaker Pattern (Per-Shard)**
  - Automatic detection of failing shards
  - Graceful degradation when shard unavailable
  - Configurable failure thresholds and timeout
  - Half-open state for recovery testing
  - Resilience4j integration

#### Security
- [ ] **Tenant Isolation Hardening**
  - Enhanced SQL injection prevention
  - Tenant ID parameter binding verification
  - Whitelist-based query pattern validation
  - Cross-tenant access attempt logging

- [ ] **Audit Logging**
  - All cross-tenant access attempts (including blocked)
  - Data access logging per tenant
  - Admin operations audit trail
  - Configurable audit log destinations

### Priority 1: High (Next Sprint)

#### Observability
- [ ] **Micrometer Metrics Integration**
  - Timer metrics: shard lookup, query execution, connection acquisition
  - Gauge metrics: active connections, tenant count per shard, cache size
  - Counter metrics: queries per shard, connection errors, routing failures
  - Custom tags: shard_id, tenant_id, database_type

- [ ] **Structured Logging with MDC**
  - Tenant ID in Mapped Diagnostic Context
  - Shard ID in MDC
  - Correlation IDs across async operations
  - Query logging with execution time
  - Slow query detection and logging

#### Resilience
- [ ] **Automatic Master-Replica Failover**
  - Automatic replica promotion on master failure
  - Connection retry with exponential backoff
  - Health-based replica selection
  - Configurable failover policies

- [ ] **Shard Blacklisting**
  - Temporary blacklist for unhealthy shards
  - Automatic recovery detection
  - Configurable blacklist duration
  - Admin override capabilities

#### Developer Experience
- [ ] **Configuration Validation**
  - Startup validation of all shard configurations
  - Connection testing on startup (optional)
  - Clear error messages for misconfigurations
  - Shard URL reachability checks
  - Duplicate shard ID detection

- [ ] **Test Utilities**
  - `@WithTenantContext` annotation for tests
  - Mock TenantContext for unit tests
  - TestContainers configuration helpers
  - Multi-tenant test data builders
  - Shard setup utilities for integration tests

### Priority 2: Medium (Future Sprints)

#### Performance
- [ ] **Query Result Caching**
  - @Cacheable support for tenant-scoped queries
  - Tenant-aware cache keys
  - Cache invalidation strategies (TTL, event-based)
  - Multi-level caching (L1: Caffeine, L2: Redis)

- [ ] **Smart Read Replica Routing**
  - Load-based replica selection (not just round-robin)
  - Replica lag monitoring
  - Latency-based routing
  - Automatic failover to master if replica unavailable

- [ ] **Batch Query Optimization**
  - Bulk operations across multiple tenants
  - Parallel query execution for multi-tenant ops
  - Tenant iterator improvements
  - Batch size optimization

- [ ] **Connection Pool Optimization**
  - Dynamic pool sizing based on load
  - Connection pool pre-warming on startup
  - Idle connection timeout tuning per shard
  - Pool size recommendations based on metrics

#### Operational Features
- [ ] **Tenant Migration Tools**
  - Live tenant migration between shards
  - Zero-downtime migration support
  - Migration progress tracking and reporting
  - Automatic data verification
  - Migration rollback capabilities

- [ ] **Shard Rebalancing**
  - Automatic tenant rebalancing across shards
  - Manual rebalance triggers via API
  - Cost-based balancing strategies
  - Capacity planning recommendations

- [ ] **Migration Enhancements**
  - Pre-migration validation hooks
  - Post-migration verification hooks
  - Migration dry-run with detailed report
  - Migration scheduling (time-based)
  - Emergency migration stop/pause
  - Migration retry on failure

#### Observability
- [ ] **OpenTelemetry Distributed Tracing**
  - Trace tenant context propagation
  - Trace queries across shards
  - Span for each shard lookup
  - Cross-DataSource operation tracking
  - Migration execution tracing

#### Testing
- [ ] **Increase Test Coverage to 90%+**
  - Edge case testing
  - Negative path testing
  - Concurrent access stress tests
  - Connection pool exhaustion tests

- [ ] **Performance Testing Framework**
  - Load testing utilities
  - Benchmark suite for common operations
  - Connection pool stress tests
  - Migration performance benchmarks
  - Multi-tenant concurrent load tests

- [ ] **Chaos Engineering**
  - Shard failure simulation
  - Network latency injection
  - Connection timeout testing
  - Partial shard availability tests
  - Random failure injection

### Priority 3: Nice to Have (Long Term)

#### Advanced Features
- [ ] **Multi-Region Support**
  - Region-aware shard routing
  - Geographic distribution of shards
  - Latency-based shard selection
  - GDPR data residency compliance
  - Cross-region replication

- [ ] **Cross-Shard Operations**
  - Limited cross-shard JOIN support
  - Aggregation across multiple shards
  - Distributed query coordination
  - Cross-shard transaction patterns

- [ ] **Saga Pattern Support**
  - Distributed transaction rollback
  - Compensation handlers
  - Saga state persistence
  - Timeout and retry policies

- [ ] **Alternative Sharding Strategies**
  - Hash-based sharding (in addition to directory)
  - Range-based sharding
  - Composite sharding keys
  - Custom sharding algorithm plugins

- [ ] **Reactive Support (Spring WebFlux)**
  - Reactive TenantContext propagation
  - R2DBC support for reactive database access
  - Non-blocking shard lookup
  - Reactive transaction management
  - Reactor Context integration

- [ ] **GraphQL Support**
  - Multi-tenant GraphQL gateway
  - Automatic shard-aware resolvers
  - Cross-shard GraphQL queries
  - GraphQL federation support

#### Security
- [ ] **Row-Level Security (RLS)**
  - PostgreSQL RLS policy generation
  - MySQL view-based isolation
  - Automatic security verification
  - RLS testing utilities

- [ ] **Encryption**
  - Per-tenant encryption keys
  - Key rotation support
  - Transparent data encryption (TDE) configuration
  - Enforce SSL/TLS for all connections
  - Certificate validation

#### Operational Features
- [ ] **Dynamic Configuration**
  - Runtime shard addition/removal
  - Hot-reload of shard configurations
  - Configuration externalization (Consul, Spring Cloud Config)
  - Feature flags for gradual rollout

- [ ] **Backup & Recovery**
  - Per-shard backup coordination
  - Point-in-time recovery support
  - Tenant data export/import APIs
  - Cross-shard consistency snapshots

- [ ] **Tenant Lifecycle Management**
  - Tenant provisioning API
  - Tenant deactivation/archival
  - Tenant deletion with cleanup
  - Tenant onboarding automation

#### Developer Tools
- [ ] **CLI Tools**
  - Tenant inspector (show info, shard, stats)
  - Shard health checker
  - Migration executor CLI
  - Configuration validator

- [ ] **Admin UI Dashboard**
  - Shard topology visualization
  - Tenant distribution dashboard
  - Real-time metrics dashboard
  - Migration management UI
  - Tenant search and management

- [ ] **IDE Plugins**
  - IntelliJ IDEA plugin (live shard info, warnings)
  - VS Code extension (snippets, indicators)
  - Tenant context indicators in IDE

#### Documentation
- [ ] **Interactive Examples**
  - More sample use cases
  - Performance tuning guide
  - Troubleshooting cookbook
  - Video tutorials

- [ ] **Migration Path Documentation**
  - From single DB to sharded architecture
  - Tenant migration between shards guide
  - Shard split/merge strategies
  - Production cutover checklists

---

## 🎯 Quick Wins (Easy + High Impact)

These items provide significant value with relatively low effort:

1. ✅ **Spotless code formatting** (DONE) - 1 hour
2. **Spring Boot Actuator health indicators** - 2-3 hours
3. **Structured logging with MDC (tenant_id/shard_id)** - 1-2 hours
4. **Configuration validation on startup** - 2-3 hours
5. **@WithTenantContext test annotation** - 3-4 hours
6. **Connection leak detection (basic)** - 3-4 hours
7. **Slow query logging** - 2-3 hours
8. **Shard status endpoint (actuator)** - 2 hours

**Total estimated time for all quick wins: ~15-20 hours**

---

## 📊 Progress Tracking

### Sprint 1 (Current)
- [x] Async context propagation (TenantContextTaskDecorator)
- [x] Spotless code formatting
- [x] Documentation reorganization
- [ ] Health indicators (in progress)

### Sprint 2 (Next)
- [ ] Connection leak detection
- [ ] Circuit breaker pattern
- [ ] Basic Micrometer metrics
- [ ] Configuration validation

### Sprint 3 (Planned)
- [ ] Structured logging with MDC
- [ ] Test utilities (@WithTenantContext)
- [ ] Automatic failover
- [ ] Security hardening

---

## 💡 Contribution Guidelines

Want to implement one of these features?

1. Check if it's already in progress (create an issue)
2. Discuss the approach in the issue
3. Follow existing code patterns and conventions
4. Include comprehensive tests
5. Update documentation
6. Run `mvn spotless:apply` before committing
7. Create a pull request with clear description

---

## 🔗 Related Documents

- [IMPROVEMENTS.md](../IMPROVEMENTS.md) - Detailed improvement descriptions
- [CLAUDE.md](../CLAUDE.md) - Development patterns and commands
- [Getting Started](guides/getting-started.md) - Library usage guide
- [Technical Specification](reference/specification.md) - Architecture details

---

**Last Updated**: 2026-04-01
**Library Version**: 1.0.0
