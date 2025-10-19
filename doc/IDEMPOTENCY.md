# Migration Idempotency Guide

## Overview

This document explains the idempotency guarantees and behaviors of the Liquibase migration system.

---

## **TL;DR**

✅ **YES** - Migrations are **idempotent at the database level**
⚠️ **WITH PROTECTION** - Concurrent execution is **prevented** via application-level locking
✅ **SAFE** - You can retry failed migrations without issues

---

## Idempotency at Different Levels

### 1. Database-Level Idempotency ✅

**Guaranteed by Liquibase**

Liquibase ensures database-level idempotency through its change tracking mechanism:

```
┌─────────────────────────────────┐
│  DATABASECHANGELOG Table        │
├─────────────────────────────────┤
│ id | author | filename | hash   │
├─────────────────────────────────┤
│ 1  | system | v1.0.xml | abc123 │
│ 2  | system | v1.0.xml | def456 │
│ 3  | system | v1.1.xml | ghi789 │
└─────────────────────────────────┘
```

**How it works:**

1. Before executing a changeset, Liquibase checks `DATABASECHANGELOG`
2. If the changeset ID already exists, it **skips** execution
3. Only new/unexecuted changesets are run
4. Each database (global + all shards) has its own changelog

**Example:**

```bash
# First execution
curl -X POST "localhost:8080/api/admin/migrations/execute"
# Result: Executes 5 new changesets on each shard

# Second execution (immediately after)
curl -X POST "localhost:8080/api/admin/migrations/execute"
# Result: All shards return SKIPPED status (0 changesets executed)
```

**Code reference:**

```java
// LiquibaseMigrationOrchestrator.java:296-306
int unrunChangeSets = liquibase.listUnrunChangeSets(
        new Contexts(contexts),
        new LabelExpression()
).size();

if (unrunChangeSets == 0) {
    progressTracker.skipMigration(dbId, "No pending migrations");
    return ShardMigrationResult.skipped(dbId, "No pending migrations");
}
```

---

### 2. Orchestration-Level Protection 🔒

**Application-Level Locking (NEW)**

To prevent resource waste and conflicts, we've added **application-level locking**:

```java
// LiquibaseMigrationOrchestrator.java:64-68
public MigrationReport migrateAll(MigrationStrategy strategy) {
    // Try to acquire lock to prevent concurrent executions
    if (!lockManager.tryAcquireLock()) {
        throw new MigrationException(
            "Migration already in progress. Cannot start concurrent migration.");
    }
    // ... migration logic ...
}
```

**Behavior:**

| Scenario | Result |
|----------|--------|
| **First request arrives** | ✅ Lock acquired, migration starts |
| **Second request (during migration)** | ❌ HTTP 409 Conflict returned |
| **Second request (after migration)** | ✅ Lock acquired, shards return SKIPPED |

**API Response on Conflict:**

```bash
curl -X POST "localhost:8080/api/admin/migrations/execute"
```

**Response (HTTP 409):**
```json
{
  "error": "MIGRATION_IN_PROGRESS",
  "message": "Migration already in progress. Cannot start concurrent migration."
}
```

---

### 3. Per-Database Locking (Liquibase) 🔐

**Liquibase's Built-in Lock**

Liquibase has its own per-database locking mechanism:

```
┌──────────────────────────────────┐
│  DATABASECHANGELOGLOCK Table     │
├──────────────────────────────────┤
│ ID | LOCKED | LOCKGRANTED | ...  │
├──────────────────────────────────┤
│ 1  | false  | NULL        | ...  │
└──────────────────────────────────┘
```

**Prevents:**
- Two Liquibase instances modifying the same database simultaneously
- Corruption from concurrent schema changes

**Protects against:**
- Multiple app instances running migrations
- Manual Liquibase CLI execution during automated migration
- Distributed deployment scenarios

---

## Idempotency Scenarios

### Scenario 1: Double Execution (Same Request)

```bash
# Execute twice in a row
curl -X POST "localhost:8080/api/admin/migrations/execute" &
curl -X POST "localhost:8080/api/admin/migrations/execute" &
```

**Result:**

**First Request:**
```json
{
  "totalShards": 10,
  "successCount": 10,
  "changeSetExecuted": 5
}
```

**Second Request (HTTP 409):**
```json
{
  "error": "MIGRATION_IN_PROGRESS",
  "message": "Migration already in progress..."
}
```

**After first completes:**
```bash
curl -X POST "localhost:8080/api/admin/migrations/execute"
```

**Response:**
```json
{
  "totalShards": 10,
  "successCount": 0,
  "skippedCount": 10,
  "results": [
    {
      "shardId": "shard1",
      "status": "SKIPPED",
      "errorMessage": "No pending migrations"
    }
  ]
}
```

---

### Scenario 2: Partial Failure Recovery

**Initial Run (Wave 3 fails):**

```
Wave 1: ✅ shard1, shard2, shard3, shard4, shard5
Wave 2: ✅ shard6, shard7, shard8, shard9, shard10
Wave 3: ❌ shard11, shard12 (FAILED), shard13, shard14, shard15 (NOT STARTED)
```

**Retry Migration:**

```bash
curl -X POST "localhost:8080/api/admin/migrations/execute?strategy=WAVE"
```

**Result:**

```
Wave 1: ⏭️ shard1-5 SKIPPED (already executed)
Wave 2: ⏭️ shard6-10 SKIPPED (already executed)
Wave 3:
  - shard11: ⏭️ SKIPPED
  - shard12: ✅ SUCCESS (retried successfully)
  - shard13: ✅ SUCCESS
  - shard14: ✅ SUCCESS
  - shard15: ✅ SUCCESS
```

**This is IDEAL for recovery!** You don't need to manually track which shards failed.

---

### Scenario 3: New Changesets Added

**Initial State:** All shards at v1.1.0 (5 changesets executed)

**Add new changeset:** `v1.2.0-add-email-verification.xml` (3 new changesets)

**Run Migration:**

```bash
curl -X POST "localhost:8080/api/admin/migrations/execute"
```

**Result:**

```json
{
  "totalShards": 10,
  "successCount": 10,
  "results": [
    {
      "shardId": "shard1",
      "status": "SUCCESS",
      "changeSetExecuted": 3,  // Only new changesets
      "currentVersion": "v1.1.0",
      "targetVersion": "v1.2.0"
    }
  ]
}
```

**Old changesets are automatically skipped!**

---

## Non-Idempotent Aspects

### 1. Execution Time ⏱️

Each run takes time, even if skipping:

```
Run 1: 5 minutes (executing changesets)
Run 2: 30 seconds (checking + skipping)
Run 3: 30 seconds (checking + skipping)
```

**Not truly idempotent in terms of resource usage.**

---

### 2. Progress Tracking 📊

Progress tracking is **overwritten** on each execution:

```java
// MigrationProgressTracker.java:31-42
public void startMigration(String shardId, ...) {
    // This overwrites any existing progress
    progressMap.put(shardId, progress);
}
```

**Impact:** If you query `/progress` during concurrent attempts, you may see inconsistent data.

**Mitigation:** Application-level lock prevents this in practice.

---

### 3. Audit Logs 📝

Each API call is logged separately:

```
2025-01-15 10:00:00 INFO: Starting migration with strategy: WAVE
2025-01-15 10:05:00 INFO: Starting migration with strategy: WAVE (skipped)
2025-01-15 10:06:00 INFO: Starting migration with strategy: WAVE (skipped)
```

**Impact:** Audit trails will show multiple executions.

---

## Best Practices for Idempotency

### 1. Always Write Rollback Scripts

```xml
<changeSet id="10" author="dev">
    <addColumn tableName="users">
        <column name="email_verified" type="BOOLEAN"/>
    </addColumn>

    <!-- ✅ GOOD: Rollback provided -->
    <rollback>
        <dropColumn tableName="users" columnName="email_verified"/>
    </rollback>
</changeSet>
```

### 2. Use Preconditions for Safety

```xml
<changeSet id="11" author="dev">
    <preConditions onFail="MARK_RAN">
        <not>
            <columnExists tableName="users" columnName="email_verified"/>
        </not>
    </preConditions>

    <addColumn tableName="users">
        <column name="email_verified" type="BOOLEAN"/>
    </addColumn>
</changeSet>
```

**Benefits:**
- Extra safety if Liquibase tracking gets corrupted
- Allows manual recovery
- Self-healing on retry

### 3. Check Status Before Retry

```bash
# Check if migration is in progress
status=$(curl -s http://localhost:8080/api/admin/migrations/running)

if [ "$status" == "false" ]; then
    # Safe to retry
    curl -X POST "http://localhost:8080/api/admin/migrations/execute"
fi
```

### 4. Use Unique ChangeSet IDs

```xml
<!-- ❌ BAD: Reusing IDs -->
<changeSet id="1" author="dev">...</changeSet>
<changeSet id="1" author="dev">...</changeSet>  <!-- Conflict! -->

<!-- ✅ GOOD: Unique IDs -->
<changeSet id="1" author="dev">...</changeSet>
<changeSet id="2" author="dev">...</changeSet>
<changeSet id="3" author="dev">...</changeSet>
```

### 5. Never Modify Executed ChangeSets

```xml
<!-- ❌ BAD: Modifying after execution -->
<changeSet id="5" author="dev">
    <!-- Originally: -->
    <!-- <addColumn tableName="users">
            <column name="phone" type="VARCHAR(20)"/>
         </addColumn> -->

    <!-- Modified to: -->
    <addColumn tableName="users">
        <column name="phone" type="VARCHAR(50)"/>  <!-- Changed! -->
    </addColumn>
</changeSet>
```

**Result:** Liquibase detects checksum mismatch and **fails**.

**Correct approach:** Create a new changeset:

```xml
<changeSet id="5" author="dev">
    <addColumn tableName="users">
        <column name="phone" type="VARCHAR(20)"/>
    </addColumn>
</changeSet>

<changeSet id="6" author="dev">
    <modifyDataType tableName="users" columnName="phone" newDataType="VARCHAR(50)"/>
</changeSet>
```

---

## Distributed Environments

### Multi-Instance Deployments

**Problem:** Multiple app instances might try to run migrations

**Liquibase Protection:**
```
Instance A: Acquires DB lock → Executes migration
Instance B: Tries to acquire lock → Waits → Eventually times out or succeeds after A
```

**Application Protection:**
```
Instance A: Acquires app lock → Executes migration
Instance B: tryAcquireLock() fails immediately → Returns HTTP 409
```

**Best Practice:** Run migrations from a **single designated instance** or CI/CD pipeline.

---

## Testing Idempotency

### Test 1: Double Execution

```bash
#!/bin/bash
# Run migration twice
curl -X POST "localhost:8080/api/admin/migrations/execute?strategy=SEQUENTIAL"
sleep 60  # Wait for completion

curl -X POST "localhost:8080/api/admin/migrations/execute?strategy=SEQUENTIAL"
# Expected: All shards SKIPPED
```

### Test 2: Concurrent Execution

```bash
#!/bin/bash
curl -X POST "localhost:8080/api/admin/migrations/execute" &
curl -X POST "localhost:8080/api/admin/migrations/execute" &
wait
# Expected: One succeeds, one returns HTTP 409
```

### Test 3: Partial Failure Recovery

```bash
#!/bin/bash
# Simulate failure by stopping a database mid-migration
# Then retry
curl -X POST "localhost:8080/api/admin/migrations/execute?strategy=WAVE"
# Expected: Completed shards skipped, failed shards retried
```

---

## Summary Table

| Aspect | Idempotent? | Mechanism |
|--------|-------------|-----------|
| **Database Changes** | ✅ YES | Liquibase DATABASECHANGELOG |
| **Concurrent Execution** | 🔒 PREVENTED | Application-level lock |
| **Per-Database Locking** | 🔐 PROTECTED | Liquibase DATABASECHANGELOGLOCK |
| **Already-Executed ChangeSets** | ✅ SKIPPED | Liquibase tracking |
| **Partial Failure Recovery** | ✅ SAFE | Re-run only failed shards |
| **Execution Time** | ❌ NOT IDEMPOTENT | Each run takes time |
| **Progress Tracking** | ⚠️ OVERWRITTEN | ConcurrentHashMap |
| **Audit Logs** | ❌ NOT IDEMPOTENT | Each call logged |

---

## Conclusion

The migration system is **idempotent where it matters most** - at the database level. You can safely:

✅ Retry failed migrations
✅ Run migrations multiple times
✅ Recover from partial failures
✅ Add new changesets incrementally

**However**, concurrent executions are **prevented** to avoid resource waste and confusion.

**Recommendation:** Always check migration status before retrying, and design your changesets with idempotency in mind using preconditions and proper rollback scripts.

---

**Last Updated**: January 2025
**Version**: 1.0.0
