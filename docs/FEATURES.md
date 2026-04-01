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

> **Focus Areas**: Resilience, Security, Observability Basics, Developer Experience
> 
> All other features (multi-region, reactive, GraphQL, admin UI, etc.) are **out of scope** 
> for the core library. These can be built as extensions or separate projects.

---

### Priority 0: Critical (Do First)

#### 1. Observability Basics
- [ ] **Spring Boot Actuator Health Indicators**
  - Per-shard database connectivity health
  - Connection pool health (active/idle/waiting)
  - Shard status health check (ACTIVE/MAINTENANCE/READONLY)
  - Tenant-shard mapping cache health
  - Migration status health indicator
  - **Estimated**: 4-6 hours

- [ ] **Basic Metrics Endpoints**
  - Connection pool metrics per shard (HikariCP stats)
  - Query execution time tracking
  - Tenant context lookup latency
  - Cache hit/miss rates
  - **Estimated**: 3-4 hours

#### 2. Resilience
- [ ] **Connection Leak Detection**
  - Automatic detection of unclosed connections
  - Leak reporting and alerting
  - Configurable leak threshold
  - Connection lifecycle tracking
  - **Estimated**: 4-6 hours

- [ ] **Circuit Breaker Pattern (Per-Shard)**
  - Automatic detection of failing shards (Resilience4j)
  - Graceful degradation when shard unavailable
  - Configurable failure thresholds and timeout
  - Half-open state for recovery testing
  - Auto-recovery detection
  - **Estimated**: 8-10 hours

#### 3. Security
- [ ] **Tenant Isolation Hardening**
  - Enhanced SQL injection prevention
  - Tenant ID parameter binding verification
  - Whitelist-based query pattern validation
  - Cross-tenant access attempt logging
  - **Estimated**: 6-8 hours

- [ ] **Audit Logging**
  - All cross-tenant access attempts (including blocked)
  - Data access logging per tenant
  - Admin operations audit trail
  - Configurable audit log destinations (file, database, external)
  - **Estimated**: 4-6 hours

#### 4. Developer Experience
- [ ] **Configuration Validation**
  - Startup validation of all shard configurations
  - Connection testing on startup (optional)
  - Clear error messages for misconfigurations
  - Shard URL reachability checks
  - Duplicate shard ID detection
  - **Estimated**: 3-4 hours

### Priority 1: High (Next Sprint)

#### 1. Observability Basics
- [ ] **Micrometer Metrics Integration**
  - Timer metrics: shard lookup, query execution, connection acquisition
  - Gauge metrics: active connections, tenant count per shard, cache size
  - Counter metrics: queries per shard, connection errors, routing failures
  - Custom tags: shard_id, tenant_id, database_type
  - **Estimated**: 6-8 hours

- [ ] **Structured Logging with MDC**
  - Tenant ID in Mapped Diagnostic Context
  - Shard ID in MDC
  - Correlation IDs across async operations
  - Query logging with execution time
  - Slow query detection and logging
  - **Estimated**: 4-6 hours

#### 2. Resilience
- [ ] **Automatic Master-Replica Failover**
  - Automatic replica promotion on master failure
  - Connection retry with exponential backoff
  - Health-based replica selection
  - Configurable failover policies
  - **Estimated**: 8-12 hours

- [ ] **Shard Blacklisting**
  - Temporary blacklist for unhealthy shards
  - Automatic recovery detection
  - Configurable blacklist duration
  - Admin override capabilities
  - **Estimated**: 6-8 hours

#### 3. Security
- [ ] **Connection Security Enforcement**
  - Enforce SSL/TLS for all shard connections
  - Certificate validation
  - Connection string validation (no plain text passwords in logs)
  - **Estimated**: 3-4 hours

#### 4. Developer Experience
- [ ] **Test Utilities**
  - `@WithTenantContext` annotation for tests
  - Mock TenantContext for unit tests
  - TestContainers configuration helpers
  - Multi-tenant test data builders
  - Shard setup utilities for integration tests
  - **Estimated**: 6-8 hours

- [ ] **Enhanced Error Messages**
  - Clear error messages with actionable suggestions
  - Error codes for programmatic handling
  - Context information in exceptions (tenant_id, shard_id)
  - **Estimated**: 3-4 hours

### Priority 2: Polish (Future Sprints)

#### 1. Observability Basics
- [ ] **OpenTelemetry Distributed Tracing** (Optional)
  - Trace tenant context propagation
  - Trace queries across shards
  - Span for each shard lookup
  - Cross-DataSource operation tracking
  - **Estimated**: 8-12 hours

#### 2. Resilience
- [ ] **Connection Pool Monitoring & Auto-tuning**
  - Dynamic pool sizing based on load metrics
  - Connection pool pre-warming on startup
  - Idle connection timeout tuning per shard
  - Pool size recommendations based on metrics
  - **Estimated**: 6-8 hours

- [ ] **Graceful Degradation**
  - Read-only mode when master unavailable
  - Stale cache reads when database down
  - Configurable degradation policies
  - **Estimated**: 6-8 hours

#### 3. Security
- [ ] **Enhanced Audit Logging**
  - Audit log encryption
  - Tamper-proof audit trail (append-only)
  - Compliance reporting (GDPR, SOC2)
  - **Estimated**: 6-8 hours

#### 4. Developer Experience
- [ ] **Increase Test Coverage to 90%+**
  - Edge case testing
  - Negative path testing
  - Concurrent access stress tests
  - Connection pool exhaustion tests
  - **Estimated**: 12-16 hours

- [ ] **Performance Testing Framework**
  - Load testing utilities
  - Benchmark suite for common operations
  - Connection pool stress tests
  - Migration performance benchmarks
  - Multi-tenant concurrent load tests
  - **Estimated**: 12-16 hours

- [ ] **Chaos Engineering Tests**
  - Shard failure simulation
  - Network latency injection
  - Connection timeout testing
  - Partial shard availability tests
  - **Estimated**: 8-12 hours

### Out of Scope

The following features are **intentionally excluded** from the core library:

- ❌ Multi-region support (can be built on top)
- ❌ Cross-shard queries (design anti-pattern)
- ❌ Reactive/WebFlux support (different paradigm, separate module)
- ❌ GraphQL support (specialized use case)
- ❌ Admin UI dashboard (separate application)
- ❌ CLI tools (can be scripts)
- ❌ IDE plugins (community contributions)

These can be built as **extensions, separate modules, or external tools** that leverage the core library.

---

## 🎯 Quick Wins (High Impact, Low Effort)

Start with these to get immediate production value:

1. ✅ **Spotless code formatting** (DONE)
2. **Configuration validation** - 3-4 hours
3. **Health indicators (basic)** - 4-6 hours
4. **Structured logging with MDC** - 4-6 hours
5. **Connection leak detection** - 4-6 hours

**Total: ~15-22 hours → Complete in 2-3 days**

These alone will dramatically improve production readiness.

---

## 📊 Implementation Roadmap

### Week 1: Quick Wins (Foundation)
- [x] Async context propagation
- [x] Spotless code formatting
- [x] Documentation reorganization
- [ ] Configuration validation (3-4h)
- [ ] Basic health indicators (4-6h)
- [ ] Structured logging with MDC (4-6h)
- [ ] Connection leak detection (4-6h)

**Total: ~15-22 hours** → Achievable in 2-3 days

### Week 2: Resilience (Critical)
- [ ] Circuit breaker pattern (8-10h)
- [ ] Automatic failover (8-12h)
- [ ] Shard blacklisting (6-8h)

**Total: ~22-30 hours** → Full week

### Week 3: Security (High Priority)
- [ ] Tenant isolation hardening (6-8h)
- [ ] Audit logging (4-6h)
- [ ] Connection security enforcement (3-4h)

**Total: ~13-18 hours** → 2-3 days

### Week 4: Observability & DX (Polish)
- [ ] Micrometer metrics (6-8h)
- [ ] Test utilities (6-8h)
- [ ] Enhanced error messages (3-4h)

**Total: ~15-20 hours** → 2-3 days

### After Month 1: Polish & Advanced
- Distributed tracing (optional)
- Performance testing framework
- Chaos engineering tests
- Advanced resilience features

---

**Total for Weeks 1-4**: ~65-90 hours (8-11 working days)

This gives you a **production-ready, battle-tested library** in about a month.

---

## 💡 Contribution Guidelines

Want to implement one of these features?

1. **Check scope**: Only features in P0-P2 are in scope for core library
2. **Create issue**: Discuss approach before starting
3. **Follow patterns**: Match existing code style and conventions
4. **Test thoroughly**: Comprehensive tests required
5. **Update docs**: Keep documentation in sync
6. **Format code**: Run `mvn spotless:apply` before committing
7. **Clear PR**: Describe what, why, and how

**For out-of-scope features**: Consider creating a separate extension library that depends on `sharding-springboot-starter`.

---

## 🔗 Related Documents

- [IMPROVEMENTS.md](../IMPROVEMENTS.md) - Detailed improvement descriptions
- [CLAUDE.md](../CLAUDE.md) - Development patterns and commands
- [Getting Started](guides/getting-started.md) - Library usage guide
- [Technical Specification](reference/specification.md) - Architecture details

---

**Last Updated**: 2026-04-01
**Library Version**: 1.0.0
