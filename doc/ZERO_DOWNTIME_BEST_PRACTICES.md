# Zero-Downtime Migration Best Practices

## Overview

Based on PayPal's PostgreSQL zero-downtime strategies and industry best practices, this guide provides recommendations for your sharded Spring Boot application.

---

## Table of Contents

1. [Core Principles](#core-principles)
2. [Lock Management](#lock-management)
3. [Safe DDL Patterns](#safe-ddl-patterns)
4. [Dangerous Operations](#dangerous-operations)
5. [Implementation Strategies](#implementation-strategies)
6. [Monitoring and Rollback](#monitoring-and-rollback)

---

## Core Principles

### **1. Lock Timeout + Retry Pattern**

**Rule:** Never hold exclusive locks for more than 2-3 seconds

**Why:** Prevents query queue buildup and application timeouts

**How:**
```xml
<changeSet id="100" author="dev">
    <!-- Set lock timeout before acquiring lock -->
    <sql>SET lock_timeout = '2s'</sql>

    <addColumn tableName="users">
        <column name="phone" type="VARCHAR(20)"/>
    </addColumn>

    <rollback>
        <dropColumn tableName="users" columnName="phone"/>
    </rollback>
</changeSet>
```

### **2. One DDL Per Transaction**

**Rule:** Each changeset should contain ONE structural change

**Why:** Minimizes lock duration and deadlock risk

**Avoid:**
```xml
<!-- ❌ BAD: Multiple changes in one changeset -->
<changeSet id="101" author="dev">
    <addColumn tableName="users">
        <column name="phone" type="VARCHAR(20)"/>
    </addColumn>
    <createIndex tableName="users" indexName="idx_phone">
        <column name="phone"/>
    </createIndex>
    <addColumn tableName="orders">
        <column name="notes" type="TEXT"/>
    </addColumn>
</changeSet>
```

**Do:**
```xml
<!-- ✅ GOOD: Separate changesets -->
<changeSet id="101" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <addColumn tableName="users">
        <column name="phone" type="VARCHAR(20)"/>
    </addColumn>
</changeSet>

<changeSet id="102" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <createIndex tableName="users" indexName="idx_phone">
        <column name="phone"/>
    </createIndex>
</changeSet>
```

### **3. Use CONCURRENTLY for Indexes**

**Rule:** Always use CONCURRENTLY for index operations on large tables

```xml
<changeSet id="103" author="dev">
    <sql>
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email
        ON users(email)
    </sql>

    <rollback>
        <sql>DROP INDEX CONCURRENTLY IF EXISTS idx_users_email</sql>
    </rollback>
</changeSet>
```

**Note:** CONCURRENTLY operations:
- Cannot be wrapped in transactions (Liquibase handles this)
- Take longer to complete
- Don't block reads or writes

---

## Lock Management

### **Strategy 1: Pre-Acquire Lock with Timeout**

**Pattern:**
```sql
SET lock_timeout = '2s';

BEGIN;
    LOCK TABLE users IN ACCESS EXCLUSIVE MODE;
    -- We now have the lock
    ALTER TABLE users ADD COLUMN email_verified BOOLEAN;
COMMIT;
```

**Benefits:**
- Fails fast if can't acquire lock
- Doesn't queue behind long-running queries
- Retry logic can handle failures

### **Strategy 2: Check for Blocking Queries**

Before attempting migration, check for long-running queries:

```sql
-- Check for queries running > 1 minute
SELECT
    pid,
    usename,
    application_name,
    state,
    query,
    now() - query_start AS duration
FROM pg_stat_activity
WHERE
    state = 'active'
    AND now() - query_start > interval '1 minute'
ORDER BY duration DESC;
```

### **Strategy 3: Statement Timeout**

Complement lock_timeout with statement_timeout:

```sql
-- Prevent any statement from running too long
SET statement_timeout = '30s';
SET lock_timeout = '2s';

ALTER TABLE users ADD COLUMN phone VARCHAR(20);
```

---

## Safe DDL Patterns

### **1. Adding Columns**

#### **Simple Column (No Default)**

```xml
<!-- ✅ SAFE: Instant, no table rewrite -->
<changeSet id="200" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <addColumn tableName="users">
        <column name="phone" type="VARCHAR(20)"/>
    </addColumn>
</changeSet>
```

**Time:** ~10ms
**Lock:** Brief ACCESS EXCLUSIVE

#### **Column with NULL Default (Postgres 11+)**

```xml
<!-- ✅ SAFE: Instant in Postgres 11+ -->
<changeSet id="201" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <addColumn tableName="users">
        <column name="status" type="VARCHAR(20)" defaultValue="ACTIVE"/>
    </addColumn>
</changeSet>
```

**Time:** ~10ms (no backfill needed)
**Lock:** Brief ACCESS EXCLUSIVE

#### **Column with NOT NULL Constraint**

```xml
<!-- ⚠️ REQUIRES MULTI-STEP: Can't be done atomically -->

<!-- Step 1: Add nullable column with default -->
<changeSet id="202" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <addColumn tableName="users">
        <column name="email_verified" type="BOOLEAN" defaultValue="false"/>
    </addColumn>
</changeSet>

<!-- Step 2: Backfill existing rows (if needed) -->
<changeSet id="203" author="dev">
    <sql>
        UPDATE users
        SET email_verified = false
        WHERE email_verified IS NULL
    </sql>
</changeSet>

<!-- Step 3: Add NOT NULL constraint -->
<changeSet id="204" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <addNotNullConstraint
        tableName="users"
        columnName="email_verified"/>
</changeSet>
```

### **2. Creating Indexes**

#### **Small Tables (< 1M rows)**

```xml
<!-- ✅ OK: Regular index -->
<changeSet id="300" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <createIndex tableName="small_config" indexName="idx_key">
        <column name="config_key"/>
    </createIndex>
</changeSet>
```

#### **Large Tables (> 1M rows)**

```xml
<!-- ✅ REQUIRED: CONCURRENTLY -->
<changeSet id="301" author="dev">
    <sql>
        CREATE INDEX CONCURRENTLY idx_users_email ON users(email)
    </sql>

    <rollback>
        <sql>DROP INDEX CONCURRENTLY idx_users_email</sql>
    </rollback>
</changeSet>
```

**Time:** 5-60 minutes (depends on table size)
**Lock:** ShareUpdateExclusive (allows reads and writes)

### **3. Dropping Columns**

#### **Immediate Drop (Postgres 11+)**

```xml
<!-- ✅ SAFE: Instant in Postgres 11+ -->
<changeSet id="400" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <dropColumn tableName="users" columnName="old_field"/>
</changeSet>
```

**Time:** ~10ms (logical drop, space reclaimed later)
**Lock:** Brief ACCESS EXCLUSIVE

#### **Two-Phase Drop (Safer)**

```xml
<!-- Step 1: Stop writing to column (code deploy) -->

<!-- Step 2: Drop column -->
<changeSet id="401" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <dropColumn tableName="users" columnName="old_field"/>
</changeSet>
```

### **4. Renaming Columns**

```xml
<!-- ⚠️ DANGER: Requires code coordination -->

<!-- Step 1: Add new column -->
<changeSet id="500" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <addColumn tableName="users">
        <column name="email_address" type="VARCHAR(255)"/>
    </addColumn>
</changeSet>

<!-- Step 2: Dual-write to both columns (code deploy) -->

<!-- Step 3: Backfill new column -->
<changeSet id="501" author="dev">
    <sql>
        UPDATE users
        SET email_address = email
        WHERE email_address IS NULL
    </sql>
</changeSet>

<!-- Step 4: Switch reads to new column (code deploy) -->

<!-- Step 5: Drop old column -->
<changeSet id="502" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <dropColumn tableName="users" columnName="email"/>
</changeSet>
```

---

## Dangerous Operations

### **❌ 1. ALTER COLUMN TYPE (Table Rewrite)**

**Problem:** Rewrites entire table

```sql
-- ❌ DANGER: Locks table for hours
ALTER TABLE users ALTER COLUMN phone TYPE VARCHAR(50);
```

**Safe Alternative (Multi-Step):**

```xml
<!-- Step 1: Add new column -->
<changeSet id="600" author="dev">
    <addColumn tableName="users">
        <column name="phone_new" type="VARCHAR(50)"/>
    </addColumn>
</changeSet>

<!-- Step 2: Dual-write (code change) -->

<!-- Step 3: Backfill in batches -->
<changeSet id="601" author="dev">
    <sql>
        -- Batch backfill (run in waves)
        UPDATE users
        SET phone_new = phone
        WHERE id BETWEEN ? AND ?
    </sql>
</changeSet>

<!-- Step 4: Swap columns -->
<changeSet id="602" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <dropColumn tableName="users" columnName="phone"/>
</changeSet>

<changeSet id="603" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <renameColumn
        tableName="users"
        oldColumnName="phone_new"
        newColumnName="phone"/>
</changeSet>
```

### **❌ 2. ADD CONSTRAINT (Validation Scan)**

**Problem:** Scans entire table to validate constraint

```sql
-- ❌ DANGER: Locks table while validating
ALTER TABLE users ADD CONSTRAINT check_email_format
    CHECK (email LIKE '%@%');
```

**Safe Alternative:**

```xml
<!-- Add constraint as NOT VALID first -->
<changeSet id="700" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <sql>
        ALTER TABLE users
        ADD CONSTRAINT check_email_format
        CHECK (email LIKE '%@%') NOT VALID
    </sql>
</changeSet>

<!-- Validate constraint separately (no lock) -->
<changeSet id="701" author="dev">
    <sql>
        ALTER TABLE users
        VALIDATE CONSTRAINT check_email_format
    </sql>
</changeSet>
```

**Time:**
- Step 1: ~10ms (no validation)
- Step 2: Minutes (validates existing rows, no exclusive lock)

### **❌ 3. ADD FOREIGN KEY (Lock Both Tables)**

```sql
-- ❌ DANGER: Locks both tables
ALTER TABLE orders
    ADD CONSTRAINT fk_user
    FOREIGN KEY (user_id) REFERENCES users(id);
```

**Safe Alternative:**

```xml
<changeSet id="800" author="dev">
    <sql>SET lock_timeout = '2s'</sql>
    <sql>
        ALTER TABLE orders
        ADD CONSTRAINT fk_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        NOT VALID
    </sql>
</changeSet>

<changeSet id="801" author="dev">
    <sql>
        ALTER TABLE orders
        VALIDATE CONSTRAINT fk_user
    </sql>
</changeSet>
```

---

## Implementation Strategies

### **For Your Sharded Application**

#### **1. Add Retry Logic to Orchestrator**

Enhance `LiquibaseMigrationOrchestrator` with retry on lock timeout:

```java
private ShardMigrationResult migrateSingleDatabaseWithRetry(
        String dbId, String url, String username, String password,
        String changeLogPath, String contexts) {

    int maxRetries = 5;
    int retryDelayMs = 3000; // 3 seconds

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return migrateSingleDatabase(dbId, url, username, password,
                                        changeLogPath, contexts);
        } catch (LiquibaseException e) {
            if (e.getMessage().contains("lock timeout") && attempt < maxRetries) {
                log.warn("Lock timeout on shard {}, attempt {}/{}. Retrying in {}ms",
                         dbId, attempt, maxRetries, retryDelayMs);
                sleep(retryDelayMs);
                continue;
            }
            throw e;
        }
    }

    throw new MigrationException("Failed after " + maxRetries + " attempts");
}
```

#### **2. Pre-Migration Checks**

Add validation before migration:

```java
private void validateMigrationSafety(String shardId, Connection conn) {
    // Check for long-running queries
    String checkQuery = """
        SELECT count(*) FROM pg_stat_activity
        WHERE state = 'active'
        AND now() - query_start > interval '1 minute'
    """;

    int longRunningQueries = jdbcTemplate.queryForObject(checkQuery, Integer.class);

    if (longRunningQueries > 0) {
        log.warn("Shard {} has {} long-running queries",
                 shardId, longRunningQueries);
        // Optionally delay or skip
    }
}
```

#### **3. Set Default Lock Timeout in Configuration**

```properties
# Add to application.properties
app.sharding.migration.default-lock-timeout=2s
app.sharding.migration.default-statement-timeout=30s
app.sharding.migration.retry-attempts=5
app.sharding.migration.retry-delay-seconds=3
```

#### **4. Enhance Changeset Template**

Create a reusable template:

```xml
<!-- db/changelog/templates/safe-ddl-template.xml -->
<databaseChangeLog>
    <changeSet id="template" author="dev">
        <!-- Always set timeouts -->
        <sql>SET lock_timeout = '${lock.timeout:2s}'</sql>
        <sql>SET statement_timeout = '${statement.timeout:30s}'</sql>

        <!-- Your DDL here -->

        <rollback>
            <!-- Always provide rollback -->
        </rollback>
    </changeSet>
</databaseChangeLog>
```

---

## Monitoring and Rollback

### **1. Monitor Lock Wait Times**

```sql
-- Query to check locks
SELECT
    blocked_locks.pid AS blocked_pid,
    blocked_activity.usename AS blocked_user,
    blocking_locks.pid AS blocking_pid,
    blocking_activity.usename AS blocking_user,
    blocked_activity.query AS blocked_statement,
    blocking_activity.query AS blocking_statement,
    now() - blocked_activity.query_start AS blocked_duration
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity
    ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity
    ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

### **2. Add Monitoring Endpoint**

```java
@GetMapping("/locks")
public ResponseEntity<List<LockInfo>> checkLocks(@RequestParam String shardId) {
    return ResponseEntity.ok(migrationMonitor.getLockInfo(shardId));
}
```

### **3. Automatic Rollback on Failure**

Liquibase handles this automatically with its rollback scripts, but add monitoring:

```java
@Override
public MigrationReport migrateAll(MigrationStrategy strategy) {
    try {
        // ... migration logic ...
    } catch (Exception e) {
        log.error("Migration failed: {}", e.getMessage());

        // Notify monitoring
        alertService.sendAlert("Migration failed on shard: " + shardId);

        throw e;
    }
}
```

---

## Quick Reference Checklist

Before deploying a migration:

- [ ] Lock timeout set (`SET lock_timeout = '2s'`)
- [ ] One DDL per changeset
- [ ] Rollback script provided
- [ ] Using CONCURRENTLY for indexes on large tables
- [ ] NOT VALID used for constraints requiring validation
- [ ] Code changes coordinated for column renames/drops
- [ ] Tested on staging with production-size data
- [ ] Monitoring alerts configured
- [ ] Retry logic in place
- [ ] Wave or Canary strategy selected for production

---

## Common Patterns

### **Pattern 1: Add Non-Nullable Column**

```
Step 1: Add nullable column with default
Step 2: (Optional) Backfill existing NULL values
Step 3: Add NOT NULL constraint
```

### **Pattern 2: Change Column Type**

```
Step 1: Add new column with new type
Step 2: Dual-write to both columns
Step 3: Backfill new column
Step 4: Switch reads to new column
Step 5: Drop old column
```

### **Pattern 3: Add Foreign Key**

```
Step 1: Add FK constraint NOT VALID
Step 2: Validate constraint (no lock)
```

### **Pattern 4: Create Index on Large Table**

```
Step 1: CREATE INDEX CONCURRENTLY
Step 2: Verify index is valid
```

---

## Resources

- **PayPal Article**: PostgreSQL at Scale: Database Schema Changes Without Downtime
- **Postgres.ai**: Zero-downtime Postgres schema migrations need lock_timeout and retries
- **GoCardless Blog**: Zero-downtime Postgres migrations series
- **Postgres Docs**: CONCURRENTLY operations

---

**Last Updated**: January 2025
**Version**: 1.0.0
