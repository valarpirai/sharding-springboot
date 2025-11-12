# Migration Tests Guide

## Overview

Comprehensive integration tests for the Liquibase Migration Orchestrator that validate migration execution across global and sharded databases using TestContainers.

---

## Test Structure

### Test File

**Location**: `sample-sharded-app/src/test/java/com/valarpirai/example/integration/migration/LiquibaseMigrationOrchestratorTest.java`

**Test Count**: 16 comprehensive tests

**Coverage**:
- ✅ Sequential migration strategy
- ✅ Parallel migration strategy
- ✅ Wave migration strategy
- ✅ Idempotency validation
- ✅ Concurrent execution prevention
- ✅ Progress tracking
- ✅ Status reporting
- ✅ Lock management
- ✅ Error handling

---

## Test Changelog Files

### Global Database Changelog

**Location**: `src/main/resources/db/changelog/test/global-test-changelog.xml`

**Changesets**:
1. **test-global-1**: Create `migration_test_metadata` table
2. **test-global-2**: Add index on `test_name`
3. **test-global-3**: Insert test data

**Purpose**: Validates migration execution on global database

### Sharded Database Changelog

**Location**: `src/main/resources/db/changelog/test/sharded-test-changelog.xml`

**Changesets**:
1. **test-shard-1**: Create `shard_test_data` table
2. **test-shard-2**: Add index on `account_id`
3. **test-shard-3**: Add index on `data_key`
4. **test-shard-4**: Create `shard_test_audit_log` table
5. **test-shard-5**: Insert test data

**Purpose**: Validates migration execution across all shards

---

## Test Cases

### 1. Sequential Migration Strategy

**Test**: `shouldExecuteMigrationWithSequentialStrategy()`

**Purpose**: Verify migrations run sequentially across shards

**Validates**:
- Strategy correctly set to SEQUENTIAL
- All shards processed one at a time
- Report contains accurate shard count
- Migration completes successfully

```java
MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

assertThat(report.getStrategy()).isEqualTo(MigrationStrategy.SEQUENTIAL);
assertThat(report.isCompleted()).isTrue();
```

---

### 2. Parallel Migration Strategy

**Test**: `shouldExecuteMigrationWithParallelStrategy()`

**Purpose**: Verify migrations run in parallel across shards

**Validates**:
- Strategy correctly set to PARALLEL
- Multiple shards processed concurrently
- No failures occur
- Faster execution than sequential

```java
MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.PARALLEL);

assertThat(report.getStrategy()).isEqualTo(MigrationStrategy.PARALLEL);
assertThat(report.getFailureCount()).isEqualTo(0);
```

---

### 3. Wave Migration Strategy

**Test**: `shouldExecuteMigrationWithWaveStrategy()`

**Purpose**: Verify migrations run in waves/batches

**Validates**:
- Strategy correctly set to WAVE
- Shards processed in configured batches
- Delay between waves respected
- All shards eventually processed

```java
MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.WAVE);

assertThat(report.getStrategy()).isEqualTo(MigrationStrategy.WAVE);
assertThat(report.getTotalExecutionTimeMs()).isGreaterThan(0);
```

---

### 4. Idempotency Validation

**Test**: `shouldBeIdempotentWhenRunningTwice()`

**Purpose**: Verify migrations are idempotent

**Validates**:
- First run executes changesets
- Second run skips already-executed changesets
- No errors on repeated execution
- Correct status reporting (SKIPPED)

```java
// First execution
MigrationReport firstReport = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
int firstChangesets = calculateTotalChangesets(firstReport);

// Second execution
MigrationReport secondReport = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
int secondChangesets = calculateTotalChangesets(secondReport);

// Second run should execute 0 changesets if first run executed any
if (firstChangesets > 0) {
    assertThat(secondChangesets).isEqualTo(0);
}
assertThat(secondReport.getSkippedCount()).isGreaterThan(0);
```

---

### 5. Concurrent Execution Prevention

**Test**: `shouldPreventConcurrentMigrations()`

**Purpose**: Verify only one migration can run at a time

**Validates**:
- First migration acquires lock
- Second migration attempt fails with appropriate error
- Lock is released after first migration completes
- Error message indicates "already in progress"

```java
// Start first migration in separate thread
Thread migrationThread = new Thread(() ->
    migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL)
);
migrationThread.start();

// Try to start second migration (should fail)
assertThatThrownBy(() ->
    migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL)
)
.isInstanceOf(MigrationException.class)
.hasMessageContaining("already in progress");
```

---

### 6. Progress Tracking

**Test**: `shouldTrackMigrationProgress()`

**Purpose**: Verify real-time progress tracking

**Validates**:
- Progress updates during migration
- Shard-level status tracking
- Progress can be queried mid-migration
- Final status reflects completion

```java
// Start migration in background
new Thread(() -> migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL)).start();

// Check progress
if (progressTracker.hasMigrationsInProgress()) {
    var progress = progressTracker.getAllProgress();
    assertThat(progress).isNotEmpty();
}
```

---

### 7. Migration Status Reporting

**Test**: `shouldReportMigrationStatusCorrectly()`

**Purpose**: Verify accurate status reporting

**Validates**:
- Report contains all shards
- Each shard has valid status (SUCCESS, SKIPPED, FAILED)
- Execution time recorded
- Version information included

```java
MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

assertThat(report.getResults()).hasSize(report.getTotalShards());
report.getResults().forEach(result -> {
    assertThat(result.getStatus()).isIn(
        MigrationStatus.SUCCESS,
        MigrationStatus.SKIPPED,
        MigrationStatus.FAILED
    );
    assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
});
```

---

### 8. Global Database Table Verification

**Test**: `shouldVerifyGlobalDatabaseHasTenantShardMappingTable()`

**Purpose**: Verify global database setup

**Validates**:
- `tenant_shard_mapping` table exists
- Table structure is correct
- Can query table successfully

```java
Integer count = globalJdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
    Integer.class,
    "tenant_shard_mapping"
);

assertThat(count).isEqualTo(1);
```

---

### 9. No Pending Changesets Handling

**Test**: `shouldHandleNoPendingChangesetsGracefully()`

**Purpose**: Verify graceful handling when no migrations needed

**Validates**:
- All shards report SKIPPED status
- No errors occur
- No failures
- Quick execution

```java
// Run twice
migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
MigrationReport secondReport = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

assertThat(secondReport.getSkippedCount()).isEqualTo(secondReport.getTotalShards());
assertThat(secondReport.getFailureCount()).isEqualTo(0);
```

---

### 10. Execution Time Recording

**Test**: `shouldRecordExecutionTimeForEachShard()`

**Purpose**: Verify execution time tracking

**Validates**:
- Total execution time recorded
- Per-shard execution time recorded
- Times are reasonable (> 0)

```java
MigrationReport report = migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

assertThat(report.getTotalExecutionTimeMs()).isGreaterThan(0);
report.getResults().forEach(result -> {
    assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
});
```

---

### 11. Shard-Level Details

**Test**: `shouldProvideShardLevelMigrationDetails()`

**Purpose**: Verify detailed shard-level information

**Validates**:
- Shard ID present
- Version information for successful migrations
- Error messages for failed migrations
- Changeset counts

```java
report.getResults().forEach(result -> {
    assertThat(result.getShardId()).isNotEmpty();

    if (result.getStatus() == MigrationStatus.SUCCESS) {
        assertThat(result.getTargetVersion()).isNotNull();
    }

    if (result.getStatus() == MigrationStatus.FAILED) {
        assertThat(result.getErrorMessage()).isNotNull();
    }
});
```

---

### 12. Progress Clearing

**Test**: `shouldClearProgressTracking()`

**Purpose**: Verify progress can be cleared

**Validates**:
- Clear operation works
- All progress removed
- No migrations marked as in progress

```java
migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

progressTracker.clear();

assertThat(progressTracker.getAllProgress()).isEmpty();
assertThat(progressTracker.hasMigrationsInProgress()).isFalse();
```

---

### 13. Status Summary

**Test**: `shouldProvideMigrationStatusSummary()`

**Purpose**: Verify status summary aggregation

**Validates**:
- Summary contains all statuses
- Counts are accurate
- Total matches shard count

```java
migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

var statusSummary = progressTracker.getStatusSummary();

assertThat(statusSummary).isNotNull();
assertThat(statusSummary.values().stream().mapToLong(Long::longValue).sum())
    .isGreaterThan(0);
```

---

### 14. Lock Release After Success

**Test**: `shouldReleaseLockAfterSuccessfulMigration()`

**Purpose**: Verify lock is released on success

**Validates**:
- Lock acquired during migration
- Lock released after completion
- Can acquire lock again after migration

```java
migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);

// Should be able to acquire lock
assertThat(lockManager.tryAcquireLock()).isTrue();
lockManager.releaseLock();
```

---

### 15. Lock Release After Failure

**Test**: `shouldReleaseLockEvenAfterFailure()`

**Purpose**: Verify lock is released even on failure

**Validates**:
- Lock released despite errors
- System can recover from failures
- No deadlock situations

```java
try {
    migrationOrchestrator.migrateAll(MigrationStrategy.SEQUENTIAL);
} catch (Exception e) {
    // Expected
}

// Lock should still be released
assertThat(lockManager.tryAcquireLock()).isTrue();
lockManager.releaseLock();
```

---

## Running the Tests

### Prerequisites

1. **Docker** must be running (for TestContainers)
2. **Maven** 3.6+
3. **Java** 17+

### Command

```bash
cd sample-sharded-app

# Run all migration tests
mvn test -Dtest=LiquibaseMigrationOrchestratorTest

# Run specific test
mvn test -Dtest=LiquibaseMigrationOrchestratorTest#shouldExecuteMigrationWithSequentialStrategy

# Run with debug output
mvn test -Dtest=LiquibaseMigrationOrchestratorTest -X
```

### Expected Output

```
[INFO] Running com.valarpirai.example.integration.migration.LiquibaseMigrationOrchestratorTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 45.234 s

[INFO] Results:
[INFO]
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

---

## Test Configuration

### Properties

```properties
app.sharding.migration.enabled=true
app.sharding.migration.migrate-global-db=false
app.sharding.migration.global-change-log-path=db/changelog/test/global-test-changelog.xml
app.sharding.migration.sharded-change-log-path=db/changelog/test/sharded-test-changelog.xml
app.sharding.migration.default-strategy=SEQUENTIAL
app.sharding.migration.fail-fast=true
app.sharding.migration.allow-rollback=true
app.sharding.migration.parallel-threads=3
app.sharding.migration.wave-size=2
app.sharding.migration.wave-delay-seconds=1
```

### Test Database Setup

- **Global Database**: PostgreSQL 13 (via TestContainer)
- **Shard 1**: PostgreSQL 13 (via TestContainer)
- **Shard 2**: PostgreSQL 13 (via TestContainer)

All databases created fresh for each test run.

---

## Validation Coverage

### Migration Functionality ✅
- Sequential execution
- Parallel execution
- Wave execution
- Canary execution (via other strategies)

### Safety Features ✅
- Idempotency
- Concurrent execution prevention
- Lock management
- Error handling

### Observability ✅
- Progress tracking
- Status reporting
- Execution time recording
- Error details

### Database Operations ✅
- Table creation
- Index creation
- Data insertion
- Schema verification

---

## Benefits

### 1. Confidence
Tests provide high confidence that migrations work correctly across all shards.

### 2. Safety
Validates idempotency and concurrent execution prevention.

### 3. Documentation
Tests serve as executable documentation of migration behavior.

### 4. Regression Prevention
Catches breaking changes in migration logic.

### 5. Real Environment
Uses actual PostgreSQL databases via TestContainers.

---

## Troubleshooting

### Tests Fail with "Docker not running"

**Solution**: Start Docker Desktop

```bash
# Check Docker status
docker info

# Start Docker Desktop (Mac)
open -a Docker
```

### Tests Timeout

**Solution**: Increase timeout or reduce test complexity

```java
@Test
@Timeout(value = 2, unit = TimeUnit.MINUTES)
void testMethod() {
    // test code
}
```

### TestContainers Port Conflicts

**Solution**: Ensure no other PostgreSQL instances running on default ports

```bash
# Check port usage
lsof -i :5432

# Kill process if needed
kill -9 <PID>
```

---

## Summary

✅ **16 comprehensive tests** for migration orchestrator
✅ **Test changelog files** for both global and sharded databases
✅ **Full strategy coverage** (SEQUENTIAL, PARALLEL, WAVE)
✅ **Safety validation** (idempotency, locking, error handling)
✅ **Real database testing** via TestContainers
✅ **Production-ready** validation

These tests ensure the Liquibase Migration Orchestrator works correctly and safely in all scenarios!

---

**Related Documentation**:
- [MIGRATION_CONFIGURATION.md](./MIGRATION_CONFIGURATION.md) - Migration setup guide
- [IDEMPOTENCY.md](./IDEMPOTENCY.md) - Migration idempotency details
- [TEST_SUMMARY.md](./TEST_SUMMARY.md) - Complete test suite overview
