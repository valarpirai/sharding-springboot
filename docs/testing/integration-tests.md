# Integration Testing Guide

## Overview

Comprehensive integration tests using **TestContainers** to verify multi-tenant sharding functionality with real PostgreSQL databases running in Docker containers.

## Prerequisites

- **Docker Desktop** installed and running
- **Maven** 3.6+
- **Java** 21+
- Internet connection (for pulling PostgreSQL images)

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MultiTenantDataIsolationTest
mvn test -Dtest=ShardingFunctionalityTest
mvn test -Dtest=LiquibaseMigrationOrchestratorTest

# Run only integration tests
mvn test -Dtest="*IT"

# Verbose output
mvn test -X
```

**Execution Time:**
- Container startup: ~10-15 seconds (cached thereafter)
- Test execution: ~30-60 seconds per test class
- Total: ~2-3 minutes for all integration tests

## Test Architecture

### Base Test Configuration

**File**: `src/test/java/com/valarpirai/example/integration/BaseIntegrationTest.java`

Sets up 3 PostgreSQL containers:
- **Global Database**: Account info and tenant-shard mappings
- **Shard 1**: First shard (latest=true, region=us-east-1)
- **Shard 2**: Second shard (latest=false, region=us-west-2)

**Configuration:**
- Database: PostgreSQL 13
- Cache: Caffeine (in-memory)
- Validation: STRICT mode
- JPA DDL: create-drop (auto-schema for tests)

### Test Isolation

- Each test runs in its own transaction
- Tenant context cleared after each test
- Database schema recreated per test run

## Test Suites

### 1. Multi-Tenant Data Isolation Tests

**File**: `MultiTenantDataIsolationTest.java` | **Tests**: 6

Validates complete data isolation between tenants:

- **Same shard isolation**: Tenants on shard1 can't see each other's data
- **Cross-shard isolation**: Tenants on different shards are fully isolated
- **Direct access prevention**: Explicit ID queries blocked across tenants
- **Concurrent operations**: Multiple tenants operating simultaneously remain isolated
- **Configuration isolation**: Roles and statuses are tenant-specific

### 2. Sharding Functionality Tests

**File**: `ShardingFunctionalityTest.java` | **Tests**: 11

Tests core sharding operations:

- **Auto-assignment**: New tenants assigned to latest shard
- **Correct routing**: Operations routed to tenant's shard
- **Tenant migration**: Moving tenants between shards
- **Shard statistics**: Distribution tracking across shards
- **Cache operations**: Hit, miss, eviction, warm-up
- **Context enforcement**: Operations fail without tenant context
- **Multi-tenant shards**: Multiple tenants coexist on same shard with isolation

### 3. Cross-Tenant Security Tests

**File**: `CrossTenantSecurityTest.java` | **Tests**: 11

Security boundary validation:

- **Read protection**: Tenants can't read others' data
- **Write protection**: Tenants can't modify others' data
- **Delete protection**: Tenants can't delete others' data
- **Context enforcement**: All operations require tenant context
- **SQL injection prevention**: account_id filtering blocks malicious queries
- **Concurrent security**: Isolation maintained under concurrent load
- **Soft-delete isolation**: Deleted data remains isolated
- **Read-only mode**: Proper enforcement and cleanup
- **Cross-tenant assignment**: Blocked across tenant boundaries

### 4. API Integration Tests

**Files**: `UserControllerApiTest.java`, `TicketControllerApiTest.java` | **Tests**: 25

End-to-end API validation with security:

**User API (12 tests):**
- Create, read, update, delete operations
- Tenant-scoped queries
- Validation error handling
- Duplicate email prevention
- Cross-tenant access blocked

**Ticket API (13 tests):**
- CRUD operations with tenant context
- Status and assignment workflows
- Search and filtering
- Priority management
- Security enforcement

### 5. Migration Tests

**File**: `LiquibaseMigrationOrchestratorTest.java` | **Tests**: 16

Validates Liquibase migration across shards:

**Strategies tested:**
- SEQUENTIAL: One shard at a time
- PARALLEL: All shards simultaneously
- WAVE: Batches with configurable size/delay
- CANARY: Test on one shard before rollout

**Features validated:**
- Idempotency (safe to re-run)
- Concurrent execution prevention (locking)
- Progress tracking across shards
- Status reporting
- Error handling and rollback
- Lock management

**Test Changelogs:**
- Global: `db/changelog/test/global-test-changelog.xml`
- Sharded: `db/changelog/test/sharded-test-changelog.xml`

## Test Coverage Summary

| Category | Tests | Coverage |
|----------|-------|----------|
| Data Isolation | 6 | Tenant separation, same/cross-shard |
| Sharding Operations | 11 | Routing, caching, migration |
| Security | 11 | Cross-tenant protection, SQL injection |
| API Endpoints | 25 | Full CRUD with security |
| Migrations | 16 | All strategies, rollback, locking |
| **Total** | **69** | **Comprehensive** |

### Entities Tested
- ✅ Account (global)
- ✅ User (sharded)
- ✅ Ticket (sharded)
- ✅ Role (sharded)
- ✅ Status (sharded)

### Functionality Validated
- ✅ Tenant-to-shard mapping and lookup
- ✅ Automatic latest shard assignment
- ✅ Data routing to correct shards
- ✅ Multi-tenant isolation (same/different shards)
- ✅ Cache operations and warm-up
- ✅ Tenant context management
- ✅ Cross-tenant security enforcement
- ✅ Concurrent operations
- ✅ Migration strategies and idempotency
- ✅ API-level security

## Dependencies

Added to `sample-sharded-app/pom.xml`:

```xml
<!-- TestContainers -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Security Test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

## Writing New Tests

### Extending BaseIntegrationTest

```java
@SpringBootTest
class MyNewIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private MyRepository myRepository;
    
    @Test
    void testMyFeature() {
        // Use tenant1Account, tenant2Account from base class
        TenantContext.executeInTenantContext(tenant1Account.getId(), () -> {
            // Your test code
        });
    }
}
```

### Using AssertJ

```java
assertThat(users)
    .hasSize(2)
    .allMatch(u -> u.getAccountId().equals(tenantId));
    
assertThat(result).isEmpty();
assertThat(mapping).isNotNull();
```

## Troubleshooting

### Docker Issues

**Tests won't start:**
- Ensure Docker Desktop is running
- Check Docker has internet access
- Verify ports 5432+ are available

**Connection errors:**
- Increase Docker memory (4GB+ recommended)
- Check Docker daemon health

### Performance

**Tests are slow:**
- TestContainers reuse mode enabled by default
- Consider local PostgreSQL for faster iteration
- Use `-Dtest=SpecificTest` to run subset

### Test Failures

**Container startup timeouts:**
- Pull images manually: `docker pull postgres:13`
- Check Docker resource limits

**Context errors:**
- Verify `@AfterEach` cleanup running
- Check for leaked tenant context

## Best Practices

1. **Always use TenantContext** for sharded operations
2. **Clean up after tests** - context, data, connections
3. **Use meaningful test names** describing scenario
4. **Test both success and failure paths**
5. **Validate security boundaries** in all features
6. **Test concurrent scenarios** for race conditions
7. **Use BaseIntegrationTest** for consistent setup

## Future Enhancements

- Performance tests with large datasets
- Concurrent load testing
- Shard failover scenarios
- Network partition handling
- Role-based access control tests
- Audit logging verification

---

**Total Lines of Test Code**: ~2,500+
**Test Execution Time**: ~2-3 minutes
**Coverage**: Multi-tenant isolation, sharding, security, migrations, API
